-- 01-create-schemas.sql
-- 创建全部业务 schema + 扩展（按 docker-entrypoint-initdb.d 字典序首先执行）
-- 依据：SYS-002 §3.7（修订版见 SYS-007 §3.10）、SYS-005、ADR-007-08

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS proc;
CREATE SCHEMA IF NOT EXISTS gov;
CREATE SCHEMA IF NOT EXISTS ds;
CREATE SCHEMA IF NOT EXISTS prod;
-- traj 预留空 schema（ADR-007-08：DDL-TRAJ-001 保留文档，暂不建表）
CREATE SCHEMA IF NOT EXISTS traj;

COMMENT ON SCHEMA iam  IS '身份认证与权限管理';
COMMENT ON SCHEMA proc IS '多模态数据处理（仅 processing-app 读写）';
COMMENT ON SCHEMA gov  IS '数据治理与资产目录';
COMMENT ON SCHEMA ds   IS '数据集与质量校验';
COMMENT ON SCHEMA prod IS '数据产品加工生成（SYS-007）';
COMMENT ON SCHEMA traj IS '北斗轨迹数据模拟与可视化（预留）';
