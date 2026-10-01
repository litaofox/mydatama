-- 05-ds-tables.sql
-- DS schema 三张表，照抄 DDL-DS-001 §3（v1.0）

CREATE TABLE IF NOT EXISTS ds.datasets (
    id          bigserial     PRIMARY KEY,
    name        varchar(128)  NOT NULL,
    scenario    varchar(64),
    description varchar(512),
    filter_cond jsonb         NOT NULL,
    creator     varchar(64)   NOT NULL,
    created_at  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by   varchar(64)   NOT NULL,
    update_by   varchar(64),
    deleted     smallint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE  ds.datasets              IS '数据集定义表';
COMMENT ON COLUMN ds.datasets.name         IS '数据集名称（同一创建人下唯一）';
COMMENT ON COLUMN ds.datasets.scenario     IS '业务场景标识';
COMMENT ON COLUMN ds.datasets.description  IS '数据集用途描述';
COMMENT ON COLUMN ds.datasets.filter_cond  IS '筛选条件 jsonb：modality/biz_domain/tags/min_quality_score/secret_level_max/asset_type';
COMMENT ON COLUMN ds.datasets.creator      IS '创建人用户名（冗余便于查询）';
COMMENT ON COLUMN ds.datasets.deleted      IS '软删除标志 0=正常 1=已删除';

CREATE UNIQUE INDEX uk_datasets_name_creator
    ON ds.datasets (name, creator) WHERE deleted = 0;
CREATE INDEX idx_datasets_creator  ON ds.datasets (creator);
CREATE INDEX idx_datasets_scenario ON ds.datasets (scenario);
CREATE INDEX idx_datasets_filter_cond ON ds.datasets USING GIN (filter_cond);

CREATE TABLE IF NOT EXISTS ds.dataset_versions (
    id             bigserial    PRIMARY KEY,
    dataset_id     bigint       NOT NULL,
    version_no     integer      NOT NULL,
    item_count     integer      NOT NULL DEFAULT 0,
    quality_report jsonb,
    change_note    varchar(512),
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      varchar(64)  NOT NULL,
    update_by      varchar(64),
    deleted        smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  ds.dataset_versions                IS '数据集版本表（快照不可变）';
COMMENT ON COLUMN ds.dataset_versions.dataset_id     IS '所属数据集 ID（逻辑关联 ds.datasets.id）';
COMMENT ON COLUMN ds.dataset_versions.version_no     IS '版本号，数据集内从 1 开始单调递增';
COMMENT ON COLUMN ds.dataset_versions.item_count     IS '版本内资产条数（冗余统计）';
COMMENT ON COLUMN ds.dataset_versions.quality_report IS '三维质量报告 jsonb：completeness/consistency/accuracy/overall_score/达标判定/文字建议';
COMMENT ON COLUMN ds.dataset_versions.change_note    IS '版本说明（用户填写，唯一允许修改的业务字段）';
COMMENT ON COLUMN ds.dataset_versions.updated_at     IS '元信息更新时间（业务字段不可变）';

CREATE UNIQUE INDEX uk_dataset_versions_ds_no
    ON ds.dataset_versions (dataset_id, version_no);
CREATE INDEX idx_dataset_versions_dataset
    ON ds.dataset_versions (dataset_id, version_no DESC);

CREATE TABLE IF NOT EXISTS ds.dataset_items (
    id             bigserial    PRIMARY KEY,
    version_id     bigint       NOT NULL,
    asset_id       bigint       NOT NULL,
    asset_snapshot jsonb        NOT NULL,
    created_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      varchar(64)  NOT NULL,
    update_by      varchar(64),
    deleted        smallint     NOT NULL DEFAULT 0
);

COMMENT ON TABLE  ds.dataset_items                 IS '数据集项表（版本内资产明细，快照不可变）';
COMMENT ON COLUMN ds.dataset_items.version_id      IS '所属版本 ID（逻辑关联 ds.dataset_versions.id）';
COMMENT ON COLUMN ds.dataset_items.asset_id        IS '资产 ID（逻辑关联 gov.assets.id，跨 schema 不加物理外键）';
COMMENT ON COLUMN ds.dataset_items.asset_snapshot  IS '资产生成时刻的快照 jsonb：name/type/modality/domain/quality_score/storage_ref/行数列数等';
COMMENT ON COLUMN ds.dataset_items.updated_at      IS '元信息更新时间（业务字段不可变）';

CREATE UNIQUE INDEX uk_dataset_items_ver_asset
    ON ds.dataset_items (version_id, asset_id);
CREATE INDEX idx_dataset_items_version ON ds.dataset_items (version_id);
CREATE INDEX idx_dataset_items_asset   ON ds.dataset_items (asset_id);
