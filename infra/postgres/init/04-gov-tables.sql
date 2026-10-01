-- 04-gov-tables.sql
-- GOV schema 九张表，照抄 DDL-GOV-001 §3（v1.0）

CREATE TABLE IF NOT EXISTS gov.assets (
    id             bigserial     PRIMARY KEY,
    name           varchar(255)  NOT NULL,
    asset_type     varchar(32)   NOT NULL,
    modality       varchar(32),
    biz_domain     varchar(64),
    secret_level   int           NOT NULL DEFAULT 1,
    owner_dept     varchar(64),
    storage_ref    varchar(512),
    source_file_id bigint,
    quality_score  numeric(5,2),
    status         varchar(32)   NOT NULL DEFAULT 'ACTIVE',
    ext            jsonb,
    create_by      varchar(64)   NOT NULL DEFAULT 'system',
    created_at     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by      varchar(64),
    updated_at     timestamp,
    deleted        smallint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE  gov.assets IS '数据资产表';
COMMENT ON COLUMN gov.assets.asset_type     IS '资产类型 DB_TABLE/DATASET_FILE/FILE/PRODUCT(PRODUCT为SYS-007扩展)';
COMMENT ON COLUMN gov.assets.modality       IS '模态 STRUCTURED/TEXT/IMAGE/VIDEO';
COMMENT ON COLUMN gov.assets.biz_domain     IS '业务域';
COMMENT ON COLUMN gov.assets.secret_level   IS '密级 1~5 ABAC判权依据';
COMMENT ON COLUMN gov.assets.owner_dept     IS '归属部门编码';
COMMENT ON COLUMN gov.assets.storage_ref    IS '存储引用 MinIO路径或库表定位符';
COMMENT ON COLUMN gov.assets.source_file_id IS '来源文件ID 逻辑关联proc.data_files.id 无物理FK';
COMMENT ON COLUMN gov.assets.quality_score  IS '质量综合评分 0~100';
COMMENT ON COLUMN gov.assets.status         IS '状态 ACTIVE/OFFLINE';
COMMENT ON COLUMN gov.assets.ext            IS '扩展属性 jsonb';
COMMENT ON COLUMN gov.assets.deleted        IS '软删标志 0=正常 1=已删除';

CREATE INDEX idx_assets_name_trgm   ON gov.assets USING GIN (name gin_trgm_ops);
CREATE INDEX idx_assets_type_domain ON gov.assets (asset_type, biz_domain);
CREATE INDEX idx_assets_modality    ON gov.assets (modality);
CREATE INDEX idx_assets_source_file ON gov.assets (source_file_id);
CREATE INDEX idx_assets_ext         ON gov.assets USING GIN (ext);

CREATE TABLE IF NOT EXISTS gov.asset_tags (
    id         bigserial    PRIMARY KEY,
    asset_id   bigint       NOT NULL,
    tag        varchar(64)  NOT NULL,
    create_by  varchar(64)  NOT NULL DEFAULT 'system',
    created_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by  varchar(64),
    updated_at timestamp,
    deleted    smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  gov.asset_tags IS '资产标签表';
COMMENT ON COLUMN gov.asset_tags.asset_id IS '资产ID 逻辑关联assets.id';
COMMENT ON COLUMN gov.asset_tags.tag      IS '标签值';

CREATE INDEX idx_asset_tags_asset ON gov.asset_tags (asset_id);
CREATE INDEX idx_asset_tags_tag   ON gov.asset_tags (tag);
CREATE UNIQUE INDEX uk_asset_tags_asset_tag
    ON gov.asset_tags (asset_id, tag) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS gov.metadata_tables (
    id         bigserial    PRIMARY KEY,
    asset_id   bigint       NOT NULL,
    table_name varchar(128) NOT NULL,
    comment    varchar(512),
    row_count  bigint,
    create_by  varchar(64)  NOT NULL DEFAULT 'system',
    created_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by  varchar(64),
    updated_at timestamp,
    deleted    smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  gov.metadata_tables IS '元数据表结构表（MVP仅承载上传结构化文件）';
COMMENT ON COLUMN gov.metadata_tables.asset_id   IS '所属资产ID 逻辑关联assets.id';
COMMENT ON COLUMN gov.metadata_tables.table_name IS '逻辑表名';
COMMENT ON COLUMN gov.metadata_tables.comment    IS '表中文说明';
COMMENT ON COLUMN gov.metadata_tables.row_count  IS '行数快照';

CREATE UNIQUE INDEX uk_metadata_tables_asset
    ON gov.metadata_tables (asset_id) WHERE deleted = 0;
CREATE INDEX idx_metadata_tables_name ON gov.metadata_tables (table_name);

CREATE TABLE IF NOT EXISTS gov.metadata_columns (
    id            bigserial    PRIMARY KEY,
    table_id      bigint       NOT NULL,
    col_name      varchar(128) NOT NULL,
    data_type     varchar(64),
    ordinal       int,
    sensitive     smallint     NOT NULL DEFAULT 0,
    mask_strategy varchar(64),
    comment       varchar(512),
    create_by     varchar(64)  NOT NULL DEFAULT 'system',
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     varchar(64),
    updated_at    timestamp,
    deleted       smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  gov.metadata_columns IS '元数据字段表';
COMMENT ON COLUMN gov.metadata_columns.table_id      IS '所属表ID 逻辑关联metadata_tables.id';
COMMENT ON COLUMN gov.metadata_columns.col_name      IS '字段名';
COMMENT ON COLUMN gov.metadata_columns.data_type     IS '数据类型';
COMMENT ON COLUMN gov.metadata_columns.ordinal       IS '字段序号';
COMMENT ON COLUMN gov.metadata_columns.sensitive     IS '是否敏感 0=否 1=是';
COMMENT ON COLUMN gov.metadata_columns.mask_strategy IS '脱敏策略 PHONE_MASK/IDCARD_MASK/NAME_MASK等';

CREATE INDEX idx_metadata_columns_table ON gov.metadata_columns (table_id);
CREATE UNIQUE INDEX uk_metadata_columns_table_col
    ON gov.metadata_columns (table_id, col_name) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS gov.lineage_edges (
    id            bigserial    PRIMARY KEY,
    from_asset_id bigint       NOT NULL,
    to_asset_id   bigint       NOT NULL,
    rel_type      varchar(32)  NOT NULL,
    ext           jsonb,
    create_by     varchar(64)  NOT NULL DEFAULT 'system',
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by     varchar(64),
    updated_at    timestamp,
    deleted       smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  gov.lineage_edges IS '血缘边表（有向：上游→下游）';
COMMENT ON COLUMN gov.lineage_edges.from_asset_id IS '上游资产ID 逻辑关联assets.id';
COMMENT ON COLUMN gov.lineage_edges.to_asset_id   IS '下游资产ID 逻辑关联assets.id';
COMMENT ON COLUMN gov.lineage_edges.rel_type      IS '关系类型 DERIVED_FROM/ANNOTATED_BY/REFERENCED_BY/DERIVED_BY';
COMMENT ON COLUMN gov.lineage_edges.ext           IS '关系扩展信息 jsonb';

CREATE INDEX idx_lineage_edges_from ON gov.lineage_edges (from_asset_id);
CREATE INDEX idx_lineage_edges_to   ON gov.lineage_edges (to_asset_id);
CREATE UNIQUE INDEX uk_lineage_edges_edge
    ON gov.lineage_edges (from_asset_id, to_asset_id, rel_type) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS gov.data_standards (
    id          bigserial    PRIMARY KEY,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    rule_expr   text,
    description varchar(512),
    enabled     smallint     NOT NULL DEFAULT 1,
    create_by   varchar(64)  NOT NULL DEFAULT 'system',
    created_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   varchar(64),
    updated_at  timestamp,
    deleted     smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  gov.data_standards IS '数据标准表';
COMMENT ON COLUMN gov.data_standards.code      IS '标准编码 唯一';
COMMENT ON COLUMN gov.data_standards.rule_expr IS '标准规则表达式';
COMMENT ON COLUMN gov.data_standards.enabled   IS '是否启用 0=否 1=是';

CREATE UNIQUE INDEX uk_data_standards_code
    ON gov.data_standards (code) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS gov.quality_rules (
    id          bigserial    PRIMARY KEY,
    target_ref  varchar(255) NOT NULL,
    check_type  varchar(32)  NOT NULL,
    expr        text,
    standard_id bigint,
    enabled     smallint     NOT NULL DEFAULT 1,
    create_by   varchar(64)  NOT NULL DEFAULT 'system',
    created_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by   varchar(64),
    updated_at  timestamp,
    deleted     smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  gov.quality_rules IS '质量规则表';
COMMENT ON COLUMN gov.quality_rules.target_ref  IS '校验目标引用 asset:{id}/table:{tid}.col:{cname}';
COMMENT ON COLUMN gov.quality_rules.check_type  IS '校验类型 NOT_NULL/REGEX/RANGE/FORMAT等';
COMMENT ON COLUMN gov.quality_rules.expr        IS '校验表达式';
COMMENT ON COLUMN gov.quality_rules.standard_id IS '引用数据标准ID 逻辑关联data_standards.id';
COMMENT ON COLUMN gov.quality_rules.enabled     IS '是否启用 0=否 1=是';

CREATE INDEX idx_quality_rules_target ON gov.quality_rules (target_ref);
CREATE INDEX idx_quality_rules_type   ON gov.quality_rules (check_type);

CREATE TABLE IF NOT EXISTS gov.quality_task_results (
    id          bigserial    PRIMARY KEY,
    rule_id     bigint       NOT NULL,
    run_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_count bigint,
    pass_count  bigint,
    pass_rate   numeric(5,2),
    detail      jsonb,
    create_by   varchar(64)  NOT NULL DEFAULT 'system',
    created_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  gov.quality_task_results IS '质量任务结果表';
COMMENT ON COLUMN gov.quality_task_results.rule_id  IS '规则ID 逻辑关联quality_rules.id';
COMMENT ON COLUMN gov.quality_task_results.run_at   IS '执行时间';
COMMENT ON COLUMN gov.quality_task_results.pass_rate IS '通过率 0~100';
COMMENT ON COLUMN gov.quality_task_results.detail   IS '执行明细 jsonb';

CREATE INDEX idx_quality_results_rule_time ON gov.quality_task_results (rule_id, run_at DESC);
CREATE INDEX idx_quality_results_run_at    ON gov.quality_task_results (run_at DESC);

CREATE TABLE IF NOT EXISTS gov.asset_usage_stats (
    id         bigserial   PRIMARY KEY,
    asset_id   bigint      NOT NULL,
    biz_domain varchar(64) NOT NULL,
    stat_date  date        NOT NULL,
    cnt        int         NOT NULL DEFAULT 0,
    created_at timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  gov.asset_usage_stats IS '资产使用统计表（域×日count 服务热力图）';
COMMENT ON COLUMN gov.asset_usage_stats.asset_id   IS '资产ID 逻辑关联assets.id';
COMMENT ON COLUMN gov.asset_usage_stats.biz_domain IS '业务域（冗余 免回表聚合）';
COMMENT ON COLUMN gov.asset_usage_stats.stat_date  IS '统计日';
COMMENT ON COLUMN gov.asset_usage_stats.cnt        IS '当日使用次数';

CREATE UNIQUE INDEX uk_asset_usage_stats_asset_date
    ON gov.asset_usage_stats (asset_id, stat_date);
CREATE INDEX idx_asset_usage_stats_domain_date
    ON gov.asset_usage_stats (biz_domain, stat_date);
