-- 07-seed-data.sql
-- 种子数据：角色/权限点/初始用户/ABAC策略/数据标准/质量规则/产品模板
-- 依据：DDL-IAM-001 §4、DDL-GOV-001 §4、SYS-007 §3.4 权限点与 §3.2.3 模板

-- ===== 1. 权限点（按模块分组，含 SYS-007 新增 PROD 九项） =====
INSERT INTO iam.permissions (code, name, module, resource, action, description, create_by) VALUES
-- IAM 模块
('iam:user:read',          '查询用户',     'iam',  'user',     'read',   '查询用户列表与详情',                'system'),
('iam:user:write',         '维护用户',     'iam',  'user',     'write',  '新建/修改/禁用用户',                'system'),
('iam:role:read',          '查询角色',     'iam',  'role',     'read',   '查询角色列表',                       'system'),
('iam:role:write',         '维护角色',     'iam',  'role',     'write',  '新建/修改角色及授权',                'system'),
('iam:policy:read',        '查询ABAC策略', 'iam',  'policy',   'read',   '查询 ABAC 策略',                    'system'),
('iam:policy:write',       '维护ABAC策略', 'iam',  'policy',   'write',  '新建/修改 ABAC 策略',               'system'),
('iam:apikey:manage',      '管理API Key',  'iam',  'apikey',   'manage', '创建/吊销自己的 API Key',           'system'),
('iam:audit:read',         '查询审计日志', 'iam',  'audit',    'read',   '审计日志检索',                       'system'),
('iam:audit:export',       '导出审计日志', 'iam',  'audit',    'export', '审计日志 CSV 导出',                  'system'),
-- PROC 模块
('proc:file:upload',       '上传文件',     'proc', 'file',     'upload', '上传结构化/多模态文件',             'system'),
('proc:file:read',         '查询文件',     'proc', 'file',     'read',   '查询文件列表与详情',                 'system'),
('proc:file:download',     '下载文件',     'proc', 'file',     'download','下载原始/处理后文件',               'system'),
('proc:job:read',          '查询任务',     'proc', 'job',      'read',   '查询处理任务状态',                   'system'),
('proc:job:retry',         '重试任务',     'proc', 'job',      'retry',  '失败任务重试',                       'system'),
('proc:annotation:write',  '保存标注',     'proc', 'annotation','write', '人工标注保存',                       'system'),
-- GOV 模块
('gov:asset:read',         '查询资产',     'gov',  'asset',    'read',   '资产目录检索与详情',                 'system'),
('gov:asset:write',        '维护资产',     'gov',  'asset',    'write',  '资产元数据修改',                     'system'),
('gov:lineage:read',       '查询血缘',     'gov',  'lineage',  'read',   '血缘追踪查询',                       'system'),
('gov:standard:write',     '维护标准',     'gov',  'standard', 'write',  '数据标准维护',                       'system'),
-- DS 模块
('ds:dataset:read',        '查询数据集',   'ds',   'dataset',  'read',   '数据集列表与详情',                   'system'),
('ds:dataset:write',       '维护数据集',   'ds',   'dataset',  'write',  '数据集创建/版本生成',                'system'),
('ds:dataset:publish',     '发布数据集',   'ds',   'dataset',  'publish','数据集发布',                         'system'),
-- SCR 模块
('scr:screen:view',        '查看大屏',     'scr',  'screen',   'view',   '访问可视化大屏',                     'system'),
-- TRAJ 模块
('traj:track:read',        '查询轨迹',     'traj', 'track',    'read',   '轨迹查询与回放',                     'system'),
('traj:simulator:manage',  '控制模拟器',   'traj', 'simulator','manage', '启动/停止模拟器',                    'system'),
-- PROD 模块（SYS-007 §3.4）
('prod:template:read',     '查询产品模板', 'prod', 'template', 'read',   '查询产品模板列表',                   'system'),
('prod:product:read',      '查询产品',     'prod', 'product',  'read',   '产品列表与详情',                     'system'),
('prod:product:write',     '维护产品',     'prod', 'product',  'write',  '产品创建/配置/修改',                 'system'),
('prod:product:generate',  '执行产品生成', 'prod', 'product',  'generate','执行生成引擎',                       'system'),
('prod:compliance:read',   '查询合规结果', 'prod', 'compliance','read',  '合规校验记录查询',                   'system'),
('prod:compliance:run',    '执行合规校验', 'prod', 'compliance','run',   '发起合规校验',                       'system'),
('prod:product:register',  '产品登记',     'prod', 'product',  'register','产品登记生成凭证号',                 'system'),
('prod:product:listing',   '产品挂牌',     'prod', 'product',  'listing','产品挂牌上架',                       'system'),
('prod:product:export',    '导出产品',     'prod', 'product',  'export', '导出产品包/说明书',                  'system')
ON CONFLICT (code) WHERE deleted = 0 DO NOTHING;

-- ===== 2. 内置角色 =====
INSERT INTO iam.roles (code, name, description, builtin, create_by) VALUES
('admin',    '系统管理员', '拥有全部权限',                 true, 'system'),
('operator', '数据运营',   '数据处理/资产/数据集/产品日常操作', true, 'system'),
('viewer',   '只读用户',   '仅查看资产/大屏/审计(受限)',   true, 'system')
ON CONFLICT (code) WHERE deleted = 0 DO NOTHING;

-- ===== 3. 角色-权限绑定 =====
-- admin：全量权限
INSERT INTO iam.role_permissions (role_id, permission_id, create_by)
SELECT r.id, p.id, 'system'
FROM iam.roles r CROSS JOIN iam.permissions p
WHERE r.code = 'admin' AND r.deleted = 0 AND p.deleted = 0
ON CONFLICT DO NOTHING;

-- operator：业务操作权限（不含 IAM 管理与审计导出；产品挂牌仅 admin）
INSERT INTO iam.role_permissions (role_id, permission_id, create_by)
SELECT r.id, p.id, 'system'
FROM iam.roles r JOIN iam.permissions p ON p.code IN (
    'proc:file:upload','proc:file:read','proc:file:download',
    'proc:job:read','proc:job:retry','proc:annotation:write',
    'gov:asset:read','gov:asset:write','gov:lineage:read','gov:standard:write',
    'ds:dataset:read','ds:dataset:write','ds:dataset:publish',
    'prod:template:read','prod:product:read','prod:product:write',
    'prod:product:generate','prod:compliance:read','prod:compliance:run',
    'prod:product:register','prod:product:export',
    'scr:screen:view',
    'traj:track:read','traj:simulator:manage',
    'iam:apikey:manage'
)
WHERE r.code = 'operator' AND r.deleted = 0 AND p.deleted = 0
ON CONFLICT DO NOTHING;

-- viewer：只读权限（无 PROD 写权限，演示 403 越权）
INSERT INTO iam.role_permissions (role_id, permission_id, create_by)
SELECT r.id, p.id, 'system'
FROM iam.roles r JOIN iam.permissions p ON p.code IN (
    'proc:file:read','proc:job:read',
    'gov:asset:read','gov:lineage:read',
    'ds:dataset:read',
    'prod:product:read','prod:compliance:read',
    'scr:screen:view',
    'traj:track:read',
    'iam:audit:read'
)
WHERE r.code = 'viewer' AND r.deleted = 0 AND p.deleted = 0
ON CONFLICT DO NOTHING;

-- ===== 4. 初始用户（口令 Admin@123 / Operator@123 / Viewer@123，BCrypt cost=10） =====
INSERT INTO iam.users (username, password_hash, real_name, dept_code, secret_level, enabled, create_by) VALUES
('admin',   '$2b$10$sgOFUsRF.WL5qeJHlLOosOQhmGG9I.OKrEETA6O0M9OmFgz028.t6',   '系统管理员', 'IT',     4, true, 'system'),
('operator','$2b$10$9yCY3GdFPYXLClUg.efGoO0lKYiXCP8P3ws.g9hm.hOTsL75SltQK',  '运营账号',   'DATA',   3, true, 'system'),
('viewer',  '$2b$10$v4u/fn0XXd9uSs6AIwBg1eMWzRPgJWbQPr3XMxtN6kzq43XRf3kNa',  '演示查看',   'GUEST',  1, true, 'system')
ON CONFLICT (username) WHERE deleted = 0 DO NOTHING;

INSERT INTO iam.user_roles (user_id, role_id, create_by)
SELECT u.id, r.id, 'system'
FROM iam.users u JOIN iam.roles r ON r.code IN ('admin')
WHERE u.username = 'admin' AND u.deleted = 0 AND r.deleted = 0
ON CONFLICT DO NOTHING;

INSERT INTO iam.user_roles (user_id, role_id, create_by)
SELECT u.id, r.id, 'system'
FROM iam.users u JOIN iam.roles r ON r.code IN ('operator')
WHERE u.username = 'operator' AND u.deleted = 0 AND r.deleted = 0
ON CONFLICT DO NOTHING;

INSERT INTO iam.user_roles (user_id, role_id, create_by)
SELECT u.id, r.id, 'system'
FROM iam.users u JOIN iam.roles r ON r.code IN ('viewer')
WHERE u.username = 'viewer' AND u.deleted = 0 AND r.deleted = 0
ON CONFLICT DO NOTHING;

-- ===== 5. 默认 ABAC 策略 =====
-- 与 DDL-IAM-001 §4.3 差异说明：演示需多角色互通可见，策略 1 去掉 dept_eq 仅保留密级判定；
-- dept_eq 版本保留为示例（enabled=false），供 ABAC 配置页演示。
INSERT INTO iam.policies (code, name, resource, action, effect, condition_tree, priority, enabled, create_by) VALUES
('asset_read_default', '资产读取-密级默认策略', 'asset', 'read', 'ALLOW',
 '{"all":[{"op":"lte","left":"asset.secret_level","right":"user.secret_level"}]}'::jsonb,
 100, true, 'system'),
('asset_read_dept_demo', '资产读取-同部门+密级（示例）', 'asset', 'read', 'ALLOW',
 '{"all":[{"op":"dept_eq","left":"user.dept_code","right":"asset.owner_dept"},
           {"op":"lte","left":"asset.secret_level","right":"user.secret_level"}]}'::jsonb,
 100, false, 'system'),
('asset_secret_deny', '高密级资产禁止越权访问', 'asset', 'read', 'DENY',
 '{"all":[{"op":"gt","left":"asset.secret_level","right":"user.secret_level"}]}'::jsonb,
 10, true, 'system'),
('dataset_publish_role', '数据集发布角色限制', 'dataset', 'publish', 'ALLOW',
 '{"all":[{"op":"in","left":"user.roles","right":["admin","operator"]}]}'::jsonb,
 50, true, 'system')
ON CONFLICT (code) WHERE deleted = 0 DO NOTHING;

-- ===== 6. 数据标准（DDL-GOV-001 §4.1） =====
INSERT INTO gov.data_standards (code, name, rule_expr, description, enabled, create_by) VALUES
('STD-PHONE-001',  '手机号格式标准',   '^1[3-9]\d{9}$',     '中国大陆手机号 11 位数字', 1, 'system'),
('STD-IDCARD-001', '身份证号格式标准', '^\d{17}[\dXx]$',    '18 位身份证号，末位可为 X', 1, 'system'),
('STD-DATE-001',   '日期格式标准',     'YYYY-MM-DD',        '日期统一 ISO 格式',        1, 'system'),
('STD-AMOUNT-001', '金额取值标准',     '0,999999999.99',    '金额非负，两位小数',       1, 'system'),
('STD-PLATE-001',  '车牌号格式标准',   'JT808-PLATE',       '中国大陆民用号牌（JT/T 808）', 1, 'system')
ON CONFLICT (code) WHERE deleted = 0 DO NOTHING;

-- ===== 7. 产品模板（SYS-007 §3.2.3） =====
INSERT INTO prod.product_templates (code, name, form, description, config_schema, version, builtin, enabled, create_by) VALUES
('tpl-data-package-v1', '数据集包模板', 'DATA_PACKAGE',
 '将原料数据集版本内全部治理后文件打包为 zip，附 README 与质量报告',
 '{"includeReadme":{"type":"boolean","default":true,"label":"附README"},"flattenDirs":{"type":"boolean","default":true,"label":"扁平目录"}}'::jsonb,
 'v1', 1, 1, 'system'),
('tpl-api-service-v1', 'API 服务模板', 'API_SERVICE',
 '将原料数据集注册为开放 API 只读查询端点，并签发计量 API Key',
 '{"rowLimit":{"type":"integer","default":1000,"max":10000,"label":"单次返回行上限"}}'::jsonb,
 'v1', 1, 1, 'system'),
('tpl-report-v1', '分析报告模板', 'REPORT',
 '基于质量报告与资产指标渲染分析报告 HTML 并转 PDF',
 '{"title":{"type":"string","required":true,"label":"报告标题"},"theme":{"type":"enum","options":["business","tech"],"default":"business","label":"风格"}}'::jsonb,
 'v1', 1, 1, 'system')
ON CONFLICT (code) WHERE deleted = 0 DO NOTHING;
