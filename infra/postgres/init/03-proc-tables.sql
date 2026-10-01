-- 03-proc-tables.sql
-- PROC schema 三张表，照抄 DDL-PROC-001 §3（v1.0）

CREATE TABLE IF NOT EXISTS proc.data_files (
    id             bigserial     PRIMARY KEY,
    file_name      varchar(255)  NOT NULL,
    modality       varchar(20)   NOT NULL,
    format         varchar(20)   NOT NULL,
    raw_path       varchar(512)  NOT NULL,
    processed_path varchar(512),
    thumb_path     varchar(512),
    size_bytes     bigint        NOT NULL DEFAULT 0,
    meta           jsonb,
    secret_level   int           NOT NULL DEFAULT 1,
    biz_domain     varchar(64),
    status         varchar(20)   NOT NULL DEFAULT 'UPLOADED',
    created_at     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamp     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      varchar(64),
    update_by      varchar(64),
    deleted        smallint      NOT NULL DEFAULT 0,
    CONSTRAINT chk_data_files_modality
        CHECK (modality IN ('STRUCTURED','TEXT','IMAGE','VIDEO')),
    CONSTRAINT chk_data_files_status
        CHECK (status IN ('UPLOADED','PROCESSING','READY','FAILED')),
    CONSTRAINT chk_data_files_secret_level
        CHECK (secret_level BETWEEN 1 AND 4)
);

COMMENT ON TABLE  proc.data_files IS '数据文件表：上传文件登记、路径、模态、处理状态与指标';
COMMENT ON COLUMN proc.data_files.file_name      IS '原始文件名';
COMMENT ON COLUMN proc.data_files.modality       IS '模态枚举 STRUCTURED/TEXT/IMAGE/VIDEO';
COMMENT ON COLUMN proc.data_files.format         IS '文件格式小写扩展名 csv/json/xlsx/xls/txt/md/log/jpg/png/webp/mp4/mov/webm';
COMMENT ON COLUMN proc.data_files.raw_path       IS '原始文件路径 宿主机挂载卷 /data 下相对路径 raw/...';
COMMENT ON COLUMN proc.data_files.processed_path IS '处理后成品路径 /data/processed/... 处理成功后回填';
COMMENT ON COLUMN proc.data_files.thumb_path     IS '缩略图路径 /data/thumb/... 仅图像/视频有值';
COMMENT ON COLUMN proc.data_files.size_bytes     IS '文件大小 字节';
COMMENT ON COLUMN proc.data_files.meta           IS '处理指标 jsonb：行数/列数/缺失率/重复率/分辨率/时长/码率/敏感列数等，结构随模态不同';
COMMENT ON COLUMN proc.data_files.secret_level   IS '数据密级 1=公开 2=内部 3=秘密 4=机密，用于 ABAC';
COMMENT ON COLUMN proc.data_files.biz_domain     IS '业务域编码 finance/hr/iot 等';
COMMENT ON COLUMN proc.data_files.status         IS '文件级状态 UPLOADED/PROCESSING/READY/FAILED';

CREATE INDEX idx_data_files_modality    ON proc.data_files (modality)    WHERE deleted = 0;
CREATE INDEX idx_data_files_status      ON proc.data_files (status)      WHERE deleted = 0;
CREATE INDEX idx_data_files_biz_domain  ON proc.data_files (biz_domain)  WHERE deleted = 0;
CREATE INDEX idx_data_files_created_at  ON proc.data_files (created_at DESC) WHERE deleted = 0;

CREATE TABLE IF NOT EXISTS proc.job_queue (
    id            bigserial    PRIMARY KEY,
    file_id       bigint       NOT NULL,
    stage         varchar(30)  NOT NULL DEFAULT 'COLLECT_VALIDATE',
    status        varchar(20)  NOT NULL DEFAULT 'PENDING',
    retry_count   int          NOT NULL DEFAULT 0,
    locked_at     timestamp,
    locked_by     bigint,
    metrics       jsonb,
    mask_columns  jsonb,
    error_msg     text,
    ls_project_id varchar(64),
    ls_task_id    varchar(64),
    created_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by     varchar(64),
    update_by     varchar(64),
    deleted       smallint     NOT NULL DEFAULT 0,
    CONSTRAINT chk_job_queue_status
        CHECK (status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT chk_job_queue_stage
        CHECK (stage IN ('COLLECT_VALIDATE','CLEAN','STANDARDIZE','MASK','ANNOTATE_REGISTER')),
    CONSTRAINT chk_job_queue_retry
        CHECK (retry_count >= 0 AND retry_count <= 5)
);

COMMENT ON TABLE  proc.job_queue IS '任务队列表：五阶段流水线调度核心，worker 以 FOR UPDATE SKIP LOCKED 抢占';
COMMENT ON COLUMN proc.job_queue.file_id       IS '关联文件ID 逻辑外键 → proc.data_files.id';
COMMENT ON COLUMN proc.job_queue.stage         IS '当前阶段 COLLECT_VALIDATE/CLEAN/STANDARDIZE/MASK/ANNOTATE_REGISTER';
COMMENT ON COLUMN proc.job_queue.status        IS '任务状态 PENDING/RUNNING/SUCCEEDED/FAILED';
COMMENT ON COLUMN proc.job_queue.retry_count   IS '已重试次数 上限5 达到5次仍失败置FAILED';
COMMENT ON COLUMN proc.job_queue.locked_at     IS '抢占时间 worker抢锁时写now() 用于崩溃恢复超时判定';
COMMENT ON COLUMN proc.job_queue.locked_by     IS '抢占worker标识 默认2线程编号1/2';
COMMENT ON COLUMN proc.job_queue.metrics       IS '各阶段执行指标 jsonb {"COLLECT_VALIDATE":{"ok":true,"ms":120},...}';
COMMENT ON COLUMN proc.job_queue.mask_columns  IS '脱敏列配置与结果 jsonb [{"col":"phone","strategy":"mask","hits":523}]';
COMMENT ON COLUMN proc.job_queue.error_msg     IS '最近一次失败原因 FAILED时必填';
COMMENT ON COLUMN proc.job_queue.ls_project_id IS 'Label Studio 项目ID ANNOTATE_REGISTER阶段创建 可选';
COMMENT ON COLUMN proc.job_queue.ls_task_id    IS 'Label Studio 任务ID 回拉标注时关联';

-- 队列抢占核心索引：status 过滤 + id 排序，配合 FOR UPDATE SKIP LOCKED
CREATE INDEX idx_job_queue_status_id ON proc.job_queue (status, id) WHERE deleted = 0;
CREATE INDEX idx_job_queue_file_id   ON proc.job_queue (file_id)   WHERE deleted = 0;
-- 崩溃恢复扫描：仅对 RUNNING 任务建部分索引
CREATE INDEX idx_job_queue_locked_at ON proc.job_queue (locked_at) WHERE status = 'RUNNING';

CREATE TABLE IF NOT EXISTS proc.annotations (
    id         bigserial    PRIMARY KEY,
    file_id    bigint       NOT NULL,
    source     varchar(20)  NOT NULL,
    content    jsonb        NOT NULL,
    annotator  varchar(64),
    created_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by  varchar(64),
    update_by  varchar(64),
    deleted    smallint     NOT NULL DEFAULT 0,
    CONSTRAINT chk_annotations_source
        CHECK (source IN ('MANUAL','LABEL_STUDIO'))
);

COMMENT ON TABLE  proc.annotations IS '标注结果表：人工标注与 Label Studio 回拉内容';
COMMENT ON COLUMN proc.annotations.file_id   IS '关联文件ID 逻辑外键 → proc.data_files.id';
COMMENT ON COLUMN proc.annotations.source    IS '标注来源 MANUAL=门户人工 LABEL_STUDIO=LS回拉';
COMMENT ON COLUMN proc.annotations.content   IS '标注内容 jsonb LS标准result数组或人工标注结构 随模态不同';
COMMENT ON COLUMN proc.annotations.annotator IS '标注人用户名 真实平台用户 LS回拉时记录触发用户';

CREATE INDEX idx_annotations_file_id ON proc.annotations (file_id) WHERE deleted = 0;
CREATE INDEX idx_annotations_source  ON proc.annotations (source)  WHERE deleted = 0;
