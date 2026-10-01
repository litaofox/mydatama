-- 02-iam-tables.sql
-- IAM schema 八张表，照抄 DDL-IAM-001 §3（v1.0）

CREATE TABLE IF NOT EXISTS iam.users (
    id                  bigserial     PRIMARY KEY,
    username            varchar(64)   NOT NULL,
    password_hash       varchar(100)  NOT NULL,
    real_name           varchar(64),
    email               varchar(128),
    phone               varchar(32),
    dept_code           varchar(64),
    secret_level        integer       NOT NULL DEFAULT 1,
    enabled             boolean       NOT NULL DEFAULT true,
    failed_login_count  integer       NOT NULL DEFAULT 0,
    locked_until        timestamp,
    last_login_at       timestamp,
    last_login_ip       varchar(64),
    created_at          timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           varchar(64),
    update_by           varchar(64),
    deleted             smallint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE  iam.users                  IS '用户表';
COMMENT ON COLUMN iam.users.username         IS '登录名 全局唯一（软删后可重用）';
COMMENT ON COLUMN iam.users.password_hash    IS 'BCrypt 哈希 含盐 60字符 永不明文';
COMMENT ON COLUMN iam.users.dept_code        IS '部门编码 ABAC dept_eq 条件属性';
COMMENT ON COLUMN iam.users.secret_level     IS '数据密级 1=公开 2=内部 3=秘密 4=机密 ABAC lte 比较';
COMMENT ON COLUMN iam.users.failed_login_count IS '连续登录失败次数 >=5 触发锁定10分钟';
COMMENT ON COLUMN iam.users.locked_until     IS '锁定截止时间 NULL=未锁定 超过后自动解锁';

CREATE UNIQUE INDEX uk_users_username
    ON iam.users (username) WHERE deleted = 0;
CREATE INDEX idx_users_dept
    ON iam.users (dept_code) WHERE deleted = 0;
CREATE INDEX idx_users_enabled
    ON iam.users (enabled) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam.roles (
    id          bigserial    PRIMARY KEY,
    code        varchar(64)  NOT NULL,
    name        varchar(64)  NOT NULL,
    description varchar(255),
    builtin     boolean      NOT NULL DEFAULT false,
    created_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   varchar(64),
    update_by   varchar(64),
    deleted     smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  iam.roles         IS '角色表';
COMMENT ON COLUMN iam.roles.code    IS '角色编码 全局唯一 如 admin/operator/viewer';
COMMENT ON COLUMN iam.roles.builtin IS '内置角色 true 时禁止删除或修改 code';

CREATE UNIQUE INDEX uk_roles_code
    ON iam.roles (code) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam.permissions (
    id          bigserial    PRIMARY KEY,
    code        varchar(128) NOT NULL,
    name        varchar(128) NOT NULL,
    module      varchar(32)  NOT NULL,
    resource    varchar(64)  NOT NULL,
    action      varchar(32)  NOT NULL,
    description varchar(255),
    created_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   varchar(64),
    update_by   varchar(64),
    deleted     smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  iam.permissions        IS '权限点表';
COMMENT ON COLUMN iam.permissions.code   IS '权限码 格式 module:resource:action 全局唯一';
COMMENT ON COLUMN iam.permissions.module IS '所属模块 iam/proc/gov/ds/scr/prod/traj';

CREATE UNIQUE INDEX uk_permissions_code
    ON iam.permissions (code) WHERE deleted = 0;
CREATE INDEX idx_permissions_module
    ON iam.permissions (module, resource);

CREATE TABLE IF NOT EXISTS iam.user_roles (
    id         bigserial PRIMARY KEY,
    user_id    bigint    NOT NULL,
    role_id    bigint    NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by  varchar(64),
    update_by  varchar(64),
    deleted    smallint  NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam.user_roles IS '用户-角色关联表';

CREATE UNIQUE INDEX uk_user_roles_user_role
    ON iam.user_roles (user_id, role_id) WHERE deleted = 0;
CREATE INDEX idx_user_roles_user
    ON iam.user_roles (user_id) WHERE deleted = 0;
CREATE INDEX idx_user_roles_role_id
    ON iam.user_roles (role_id) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam.role_permissions (
    id            bigserial PRIMARY KEY,
    role_id       bigint    NOT NULL,
    permission_id bigint    NOT NULL,
    created_at    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     varchar(64),
    update_by     varchar(64),
    deleted       smallint  NOT NULL DEFAULT 0
);

COMMENT ON TABLE iam.role_permissions IS '角色-权限关联表';

CREATE UNIQUE INDEX uk_role_permissions_rp
    ON iam.role_permissions (role_id, permission_id) WHERE deleted = 0;
CREATE INDEX idx_role_permissions_role
    ON iam.role_permissions (role_id) WHERE deleted = 0;
CREATE INDEX idx_role_permissions_perm
    ON iam.role_permissions (permission_id) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam.policies (
    id             bigserial    PRIMARY KEY,
    code           varchar(64)  NOT NULL,
    name           varchar(128) NOT NULL,
    description    varchar(255),
    resource       varchar(64)  NOT NULL,
    action         varchar(32)  NOT NULL,
    effect         varchar(8)   NOT NULL DEFAULT 'ALLOW',
    condition_tree jsonb        NOT NULL,
    priority       integer      NOT NULL DEFAULT 100,
    enabled        boolean      NOT NULL DEFAULT true,
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      varchar(64),
    update_by      varchar(64),
    deleted        smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  iam.policies                IS 'ABAC 策略表';
COMMENT ON COLUMN iam.policies.effect         IS 'ALLOW / DENY';
COMMENT ON COLUMN iam.policies.condition_tree IS 'jsonb 条件树 支持 all/any/not/eq/lte 等算子';
COMMENT ON COLUMN iam.policies.priority       IS '数字小者优先 DENY 通常置高优先级';

CREATE UNIQUE INDEX uk_policies_code
    ON iam.policies (code) WHERE deleted = 0;
CREATE INDEX idx_policies_resource
    ON iam.policies (resource, action, enabled) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam.api_keys (
    id            bigserial    PRIMARY KEY,
    user_id       bigint       NOT NULL,
    name          varchar(64)  NOT NULL,
    key_prefix    varchar(16)  NOT NULL,
    key_hash      varchar(128) NOT NULL,
    scopes        varchar(512),
    expire_at     timestamp,
    last_used_at  timestamp,
    last_used_ip  varchar(64),
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     varchar(64),
    update_by     varchar(64),
    deleted       smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  iam.api_keys            IS 'API Key 表 第三方系统接入凭证';
COMMENT ON COLUMN iam.api_keys.key_prefix IS 'Key 前8位明文 用于快速定位候选记录';
COMMENT ON COLUMN iam.api_keys.key_hash   IS 'SHA-256(完整Key) 十六进制64字符 不存明文';
COMMENT ON COLUMN iam.api_keys.scopes     IS '权限范围 逗号分隔权限码 不得超过所属用户已有权限';

CREATE INDEX idx_api_keys_prefix
    ON iam.api_keys (key_prefix) WHERE deleted = 0 AND enabled = true;
CREATE INDEX idx_api_keys_user
    ON iam.api_keys (user_id) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS iam.audit_logs (
    id          bigserial    PRIMARY KEY,
    username    varchar(64),
    user_id     bigint,
    action      varchar(64)  NOT NULL,
    resource    varchar(128),
    method      varchar(8),
    path        varchar(255),
    status_code integer,
    ip          varchar(64),
    user_agent  varchar(255),
    cost_ms     bigint,
    detail      jsonb,
    trace_id    varchar(64),
    created_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  iam.audit_logs            IS '操作审计表 只追加 保留180天';
COMMENT ON COLUMN iam.audit_logs.action     IS '动作标签 LOGIN/UPLOAD/PERMISSION_GRANT/DATASET_PUBLISH/MASK_POLICY_CHANGE/API_CALL 等';
COMMENT ON COLUMN iam.audit_logs.detail     IS 'jsonb 附加信息 敏感字段需脱敏';
COMMENT ON COLUMN iam.audit_logs.created_at IS '发生时间 只追加无 updated_at';

CREATE INDEX idx_audit_logs_created_at
    ON iam.audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_username_time
    ON iam.audit_logs (username, created_at DESC);
CREATE INDEX idx_audit_logs_action_time
    ON iam.audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_logs_path_trgm
    ON iam.audit_logs USING GIN (path gin_trgm_ops);
