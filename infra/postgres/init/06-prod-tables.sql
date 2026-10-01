-- 06-prod-tables.sql
-- PROD schema 四张表，照抄 SYS-007 §3.3（v1.0）
-- 产品状态机：DRAFT→CONFIGURED→GENERATED→CHECKING→PASSED/BLOCKED→REGISTERED→LISTED→DELIVERED（演示到 LISTED）

CREATE TABLE IF NOT EXISTS prod.product_templates (
    id            bigserial    PRIMARY KEY,
    code          varchar(64)  NOT NULL,
    name          varchar(128) NOT NULL,
    form          varchar(32)  NOT NULL,
    description   varchar(512),
    config_schema jsonb,
    version       varchar(32)  NOT NULL DEFAULT 'v1',
    builtin       smallint     NOT NULL DEFAULT 0,
    enabled       smallint     NOT NULL DEFAULT 1,
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     varchar(64),
    update_by     varchar(64),
    deleted       smallint     NOT NULL DEFAULT 0,
    CONSTRAINT chk_product_templates_form
        CHECK (form IN ('DATA_PACKAGE','API_SERVICE','REPORT'))
);

COMMENT ON TABLE  prod.product_templates        IS '产品模板表：数据包/API服务/报告三类';
COMMENT ON COLUMN prod.product_templates.code   IS '模板编码 唯一';
COMMENT ON COLUMN prod.product_templates.form   IS '模板形态 DATA_PACKAGE/API_SERVICE/REPORT';
COMMENT ON COLUMN prod.product_templates.config_schema IS '模板参数声明 jsonb 前端动态表单依据';
COMMENT ON COLUMN prod.product_templates.builtin IS '内置模板 1=内置 禁止删除';

CREATE UNIQUE INDEX uk_product_templates_code
    ON prod.product_templates (code) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS prod.products (
    id                 bigserial     PRIMARY KEY,
    code               varchar(64)   NOT NULL,
    name               varchar(128)  NOT NULL,
    template_id        bigint        NOT NULL,
    form               varchar(32)   NOT NULL,
    dataset_id         bigint        NOT NULL,
    dataset_version_id bigint        NOT NULL,
    dataset_version_no integer       NOT NULL,
    secret_level       int           NOT NULL DEFAULT 1,
    category           varchar(64),
    pricing_model      varchar(32),
    price              numeric(12,2),
    description        varchar(512),
    provider           varchar(128)  NOT NULL DEFAULT 'mydatama',
    manual_meta        jsonb,
    config_params      jsonb,
    status             varchar(20)   NOT NULL DEFAULT 'DRAFT',
    reg_no             varchar(64),
    listed_at          timestamp,
    last_error         varchar(512),
    created_at         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          varchar(64)   NOT NULL,
    update_by          varchar(64),
    deleted            smallint      NOT NULL DEFAULT 0,
    CONSTRAINT chk_products_form   CHECK (form IN ('DATA_PACKAGE','API_SERVICE','REPORT')),
    CONSTRAINT chk_products_status CHECK (status IN
        ('DRAFT','CONFIGURED','GENERATED','CHECKING','PASSED','BLOCKED','REGISTERED','LISTED','DELIVERED')),
    CONSTRAINT chk_products_pricing CHECK (pricing_model IS NULL OR
        pricing_model IN ('PER_CALL','PER_MONTH','ONE_TIME')),
    CONSTRAINT chk_products_secret CHECK (secret_level BETWEEN 1 AND 4)
);

COMMENT ON TABLE  prod.products                IS '产品实例表：状态机 DRAFT→CONFIGURED→GENERATED→CHECKING→PASSED/BLOCKED→REGISTERED→LISTED';
COMMENT ON COLUMN prod.products.code           IS '产品编码 唯一 建议 PRD-{日期}-{序号}';
COMMENT ON COLUMN prod.products.template_id    IS '模板ID 逻辑关联 prod.product_templates.id';
COMMENT ON COLUMN prod.products.form           IS '形态 冗余自模板 生成引擎分支依据';
COMMENT ON COLUMN prod.products.dataset_version_id IS '原料版本ID 逻辑关联 ds.dataset_versions.id';
COMMENT ON COLUMN prod.products.secret_level   IS '产品密级 1~4 必须≥原料最大密级(合规R1)';
COMMENT ON COLUMN prod.products.pricing_model  IS '定价方式 PER_CALL按次/PER_MONTH按月/ONE_TIME买断';
COMMENT ON COLUMN prod.products.manual_meta    IS '说明书四要素 jsonb: source_desc/field_desc/update_freq/delivery_mode';
COMMENT ON COLUMN prod.products.config_params  IS '模板参数 jsonb 按模板 config_schema 填写';
COMMENT ON COLUMN prod.products.status         IS '产品状态机';
COMMENT ON COLUMN prod.products.reg_no         IS '登记凭证号 REG-{yyyyMMdd}-{6位}';
COMMENT ON COLUMN prod.products.listed_at      IS '挂牌时间';
COMMENT ON COLUMN prod.products.last_error     IS '最近一次生成/校验失败摘要';

CREATE UNIQUE INDEX uk_products_code
    ON prod.products (code) WHERE deleted = 0;
CREATE INDEX idx_products_status   ON prod.products (status)   WHERE deleted = 0;
CREATE INDEX idx_products_dataset  ON prod.products (dataset_id, dataset_version_id) WHERE deleted = 0;
CREATE INDEX idx_products_template ON prod.products (template_id) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS prod.product_artifacts (
    id            bigserial     PRIMARY KEY,
    product_id    bigint        NOT NULL,
    artifact_type varchar(32)   NOT NULL,
    file_path     varchar(512),
    file_size     bigint        NOT NULL DEFAULT 0,
    checksum      varchar(64),
    ext           jsonb,
    created_at    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     varchar(64)   NOT NULL,
    update_by     varchar(64),
    deleted       smallint      NOT NULL DEFAULT 0,
    CONSTRAINT chk_product_artifacts_type CHECK (artifact_type IN
        ('DATA_PACKAGE_ZIP','MANUAL_PDF','MANUAL_HTML','REPORT_HTML','REPORT_PDF','QUALITY_REPORT','API_KEY_REF'))
);

COMMENT ON TABLE  prod.product_artifacts             IS '产品产物表：zip/PDF/HTML/质量报告/APIKey引用';
COMMENT ON COLUMN prod.product_artifacts.artifact_type IS '产物类型';
COMMENT ON COLUMN prod.product_artifacts.file_path   IS '/data 卷相对路径 products/{code}/...；API_KEY_REF 时存 api_keys.id';
COMMENT ON COLUMN prod.product_artifacts.checksum    IS 'SHA-256 十六进制 64 字符';

CREATE INDEX idx_product_artifacts_product
    ON prod.product_artifacts (product_id) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS prod.compliance_checks (
    id          bigserial    PRIMARY KEY,
    product_id  bigint       NOT NULL,
    batch_no    varchar(32)  NOT NULL,
    rule_code   varchar(32)  NOT NULL,
    rule_name   varchar(128) NOT NULL,
    passed      smallint     NOT NULL,
    detail      varchar(1024),
    run_at      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   varchar(64)  NOT NULL,
    created_at  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_compliance_rule CHECK (rule_code IN
        ('SECRET_LEVEL','SENSITIVE_MASKED','QUALITY_THRESHOLD','MANUAL_COMPLETE'))
);

COMMENT ON TABLE  prod.compliance_checks            IS '合规校验记录表：四规则逐条结果 只追加';
COMMENT ON COLUMN prod.compliance_checks.batch_no   IS '校验批次号 同批四条一致 产品详情取最新批';
COMMENT ON COLUMN prod.compliance_checks.rule_code  IS '规则 SECRET_LEVEL/SENSITIVE_MASKED/QUALITY_THRESHOLD/MANUAL_COMPLETE';
COMMENT ON COLUMN prod.compliance_checks.passed     IS '结论 0=不通过 1=通过';
COMMENT ON COLUMN prod.compliance_checks.detail     IS '不通过原因文案(人可读)';

CREATE INDEX idx_compliance_product_batch
    ON prod.compliance_checks (product_id, batch_no, run_at DESC);
