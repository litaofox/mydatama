# 企业级多模态数据中台（组合方案）实施计划

> **v1.0 修订（2026-09-29，演示资源收紧为 Lite 架构，以此版为准）**：
> 下方"组合方案/微服务"章节（Nacos/Kafka/Redis/MinIO/多 JVM/DataEase 容器）**降级为生产演进路线参考**；MVP 实施以 `docs/01~03` v1.0 三份文档为准：
> - **容器从 10+ 减到 4 个**：postgres（唯一中间件）、platform-app（Java 模块化单体，Xmx640m）、processing-app（FastAPI 单容器含 worker）、portal-nginx；label profile 仅加 1 个 Label Studio；
> - Kafka → **PG 队列表 `SKIP LOCKED`**；Redis → Caffeine/DB 计数；MinIO → **磁盘卷 + Storage 接口**；Nacos/网关 → Nginx 路径分流 + 固定容器名；ES → PostgreSQL ILIKE；
> - **容器稳态内存 core ≤ 2.5GB、+label ≤ 3.5GB，整机 ≤ 8GB**；软件许可成本 0 元；
> - DataEase 移出本期（8GB 预算外），大屏完全自研（ECharts）；
> - 代码按模块/接口隔离（Java Maven 多模块单 Jar、Storage/JobQueue 接口抽象），保证未来无重构拆回微服务。
>
> **v0.9 修订（2026-09-29，按 MVP 需求冻结范围）**：①新增**结构化数据处理**——CSV/JSON/Excel 的导入、解析、标准化与按列自动脱敏（pandas/openpyxl，processing-worker 内实现，详见设计文档 §3.2）；②多模态明确包含**视频**（ffprobe/ffmpeg 元数据登记、首帧缩略图、元数据标签清除）；③**自研 ECharts 大屏为核心交付（默认 core 档）**，DataEase 降为可选 bi profile；④检索 MVP 用 PostgreSQL，ES 降为扩展预留；⑤交付物增加详细设计/部署演示/开销评估三份文档（见 `docs/`）。

## 一、背景与调研结论

- 工作目录 `mydatama/` 当前为空，全新绿地项目。
- 经两轮开源调研与用户确认，采用**组合方案**：自研统一平台作为主干，数据治理设计参考 AllData，多模态流水线自研，标注集成 Label Studio 容器，可视化/大屏集成 DataEase 容器，**权限统一由网关收口**。
- 开源项目实地核查结论（已核对 GitHub 官方仓库）：
  - **AllData**（github.com/alldatacenter/alldata，3.1k★，2026-09 更新）：治理模块齐全——`data-metadata`（元数据）、`data-market`（数据资产/集市）、`data-standard`（数据标准）、`data-masterdata`（主数据）、`data-quality`（质量）、`data-visual`（可视化）、`data-dts`（同步）；但工程上有三个约束：① 基于 **JHipster + Spring Cloud Netflix(Eureka)**，与我们选定的 Nacos 体系不一致；② 官方安装包按 **16GB 内存服务器**设计，捆绑大数据组件，仓库体积 1.6GB；③ 许可证 **GPL-3.0**（强传染）。
  - **DataEase v2**：国产开源 BI/大屏第一梯队，中文文档完善；官方提供容器镜像（内含前后端+core 引擎），外挂 **MySQL 8**；支持仪表板/大屏外链分享与 iframe 嵌入；许可证 **GPL-3.0**。
  - **Label Studio**（20k★，Apache-2.0）：官方容器支持 PostgreSQL，REST API 可建项目/任务/回拉标注；**社区版无 RBAC（单租户管理员模型）**，SSO 属企业版功能。
- 工程取舍：**不把 AllData 全家桶搬进 MVP**（内存、Eureka 冲突、GPL、组件捆绑风险过高），而是①自研轻量治理服务，模块划分/表设计/交互对齐 AllData（仅学习设计，不拷贝其 GPL 源码）；② AllData 作为 `gov-full` 可选 profile，以官方镜像独立运行、网关跳转集成，供二期完整治理增强。

## 二、总体架构

```
                     ┌────────────────────────────────────────────┐
                     │   统一门户 Web (Vue3 + Nginx :8090)          │
                     │  工作台/多模态/标注/治理/数据集/大屏/权限审计  │
                     └───────────┬──────────────────────────────┘
                                 │ /api/**（自研）  /label-studio/**  /bi/**
                     ┌───────────▼──────────────────────────────┐
                     │  unified-gateway (Spring Cloud Gateway)    │
                     │  JWT校验 · RBAC粗鉴权 · API-Key · 限流      │
                     │  统一审计 · 外部系统代理 + 令牌注入过滤器    │
                     └──┬───────┬────────┬────────┬─────────┬────┘
        Nacos lb://    │       │        │        │         │  反向代理(令牌收口)
   ┌────────┬──────────┴──┐ ┌──▼──────┐ │  ┌──────▼─────┐ ┌─▼──────────────┐
   │iam-svc │governance-  │ │dataset- │ │  │processing- │ │Label Studio    │
   │        │svc          │ │svc      │ │  │svc(FastAPI)│ │(Apache-2.0容器)│
   │RBAC/ABAC│资产/元数据 │ │选数/版本│ │  │+worker     │ │ 标注UI/API     │
   │审计/Key │标准/血缘    │ │质检/评分│ │  │多模态流水线│ │ (label profile)│
   │        │质量规则/热力 │ │         │ │  │LS API客户端│ └────────────────┘
   └───┬────┴──────┬──────┘ └────┬────┘ │  └──────┬─────┘
       │           │             │       │         │      ┌──────────────────┐
       │           │             │       │         └─────▶│DataEase v2 容器  │
       │           │             │       │  (bi profile 反代/iframe，连PG只读)│
       │  PostgreSQL 16          │       │                │+ MySQL8(私有库)  │
       │  iam/governance/dataset │       │                └──────────────────┘
       │  processing/labelstudio │       │      ┌─────────────────────────────┐
       └───────────┬─────────────┴───────┴─────▶│ MinIO(raw/processed/label) │
                   │                            └─────────────────────────────┘
   Redis · Kafka(lifecycle/audit) · Elasticsearch(资产检索,可降级PG)
   [可选 gov-full] AllData 官方容器栈(独立Eureka+MySQL, 网关仅做跳转聚合)
```

**集成原则（规避许可证与体系冲突）**：
- Label Studio / DataEase / AllData 均以**独立官方容器、独立进程、独立库**运行，平台不修改其源码、不与其类路径耦合，仅经 REST/iframe/反向代理集成；
- 三个外部系统**不直接对用户暴露端口**，浏览器只访问统一网关；网关校验自研 JWT 后再以服务账号令牌代理访问（补齐 Label Studio 社区版缺失的 RBAC），操作人身份写入平台审计。

## 三、技术选型

| 域 | 选型 |
|---|---|
| 自研 Java | JDK 17、Spring Boot 3.2、Spring Cloud 2023.0、Spring Cloud Alibaba 2023.0.1.2（Nacos 注册/配置）、Spring Cloud Gateway、MyBatis-Plus、spring-kafka、spring-data-elasticsearch、jjwt、minio、Redisson、springdoc |
| 多模态服务 | Python 3.11、FastAPI、uvicorn、SQLAlchemy 2、minio、kafka-python-ng、Pillow、mutagen、requests（调 Label Studio API） |
| 前端 | Vue 3.4 + Vite + TS + Element Plus + Pinia + Vue Router + ECharts；Label Studio / DataEase 页面以 iframe 嵌入 |
| 平台存储 | PostgreSQL 16（iam/governance/dataset/processing/labelstudio 五库）、Redis 7、Kafka 3.7(KRaft)、Elasticsearch 8(单节点,可关)、MinIO |
| 外部组件 | Label Studio 官方镜像（Apache-2.0）；DataEase v2 官方镜像 + MySQL 8（GPL-3.0，独立运行）；AllData 官方镜像（GPL-3.0，仅 gov-full profile） |
| 部署 | Docker Compose，**profile 分级**：`core`(默认) / `label` / `bi` / `gov-full`，按机器内存组合启用 |

## 四、代码结构（计划新增）

```
mydatama/
├── docker-compose.yml              # core(默认)+label+bi profiles；gov-full 用 override
├── docker-compose.gov-full.yml     # AllData 官方栈接入（可选，二期验证）
├── .env.example
├── infra/
│   ├── postgres/init/              # 建五库 + 平台建表/种子SQL + dataease_ro 只读账号
│   ├── mysql/                      # DataEase 专用 MySQL8 初始化(空库,DataEase自建表)
│   ├── minio/init-buckets.sh       # raw / processed / label-presign 桶
│   └── nginx/portal.conf           # 门户静态托管与 /api 反代
├── backend-java/                   # Maven 多模块
│   ├── platform-common/            # 统一返回/异常/JWT/内部令牌/AuthzClient/审计事件/Kafka/ES/MyBatis
│   ├── gateway/                    # 路由 + JWT/RBAC/APIKey/限流/审计 + LS&BI 代理令牌过滤器
│   ├── iam-service/                # 用户/角色/权限/ABAC策略/APIKey/审计日志(消费audit.logs)
│   ├── governance-service/         # 资产目录/元数据采集/数据标准/血缘/热力图/质量规则(设计参考AllData)
│   └── dataset-service/            # 训练数据集选数/版本快照/质检评分/变更对比
├── processing-service/             # Python FastAPI
│   ├── app/ (api/core/db/models/schemas/security)
│   ├── app/services/ (minio/kafka/ls_client/pipeline: cleaner/standardizer/desensitizer/annotator)
│   ├── app/services/pipeline/ (text.py image.py audio.py 三模态)
│   └── worker.py                   # 消费processing.jobs,产出lifecycle事件,回拉LS标注
├── integration/
│   ├── label-studio/env.conf       # LS 容器配置(PG库/服务令牌/关闭公开注册)
│   ├── dataease/                   # DataEase 挂载配置 + 预置大屏导入包(资产总览/处理监控)
│   └── alldata/README.md           # gov-full 接入步骤与账号映射(运行说明,非代码依赖)
├── frontend/
│   └── src/views: login/dashboard/processing/annotation/governance/datasets/screen/permission/audit/openapi
└── scripts/smoke-test.sh
```

## 五、核心数据模型（自研库，要点）

- **iam 库**：users / roles / user_roles / permissions / role_permissions / policies（ABAC 条件树 JSON：部门一致、密级≤人员密级等）/ api_keys（哈希）/ audit_logs。
- **governance 库**：datasources（JDBC 连接登记）/ metadata_tables、metadata_columns（内置 PG/MySQL 元数据采集）/ assets（统一资产表：结构化表资产 + 多模态文件资产，含模态、业务域、密级、owner、storage_uri、质量分）/ asset_tags / data_standards、standard_code_tables（标准/码表）/ lineage_nodes、lineage_edges（上下游、关系类型）/ quality_rules、quality_task_results（规则式结构化质量检查）/ asset_usage_stats（域×时段聚合，热力图）。
- **dataset 库**：datasets（业务场景+筛选条件 JSON）/ dataset_versions（版本号、快照、质量报告 JSON、变更说明）/ dataset_items（版本×资产明细）。
- **processing 库**：data_files / processing_jobs（五阶段状态与指标）/ annotations（标注内容 JSON，来源标记为人工/LS 回拉）。
- **labelstudio 库**：归 Label Studio 容器自管，平台不直接读写，仅经其 API 操作。
- 种子：管理员由 iam-service 启动时 DataRunner 创建（BCrypt）；预置业务域/密级字典/演示资产。

## 六、功能落点（MVP 边界）

1. **多模态处理（自研 FastAPI+worker）**：文本/图像/音频上传 MinIO → Kafka 流水线（采集→清洗→标准化→脱敏→标注）。文本编码归一/去重/正则掩码手机身份证银行卡；图像 Pillow 校验归一、剥离 EXIF；音频 mutagen 元数据登记与剥离；每阶段产出可用性/准确性指标，发 lifecycle 事件。
2. **智能标注（Label Studio 集成）**：processing 用服务令牌调 LS API 自动建 Project/Task（文件经 MinIO 预签名或网关联名 URL 访问）；标注完成后 worker 回拉结果写入 annotations 并更新血缘与资产；前端"智能标注"经网关 `/label-studio/**` 代理访问，网关 JWT 校验 + LS token 注入 + 操作审计，弥补社区版无 RBAC。
3. **数据治理（governance-service，模块设计对齐 AllData）**：数据源与元数据 JDBC 采集；资产自动盘点（消费 lifecycle 事件，多模态与表资产统一目录）、分类标签、密级；ES 全文检索（不可用降级 PG ILIKE）；数据标准与码表；血缘图（ECharts graph）与上下游/影响分析；业务域×时段热力图与使用频率；结构化数据质量规则（非空/唯一/正则/值域/一致性）与结果；数据资产开放 API（API-Key）。
4. **训练数据集（dataset-service）**：按场景+条件（模态/域/标签/质量阈值）选数快照建版；三维度质检——完整性(缺失<0.5%)、一致性(格式统一率100%)、准确性(规则错误率<0.1%)——加权评分与优化建议；版本列表/对比/变更追踪。
5. **可视化大屏（DataEase 集成）**：`bi` profile 启动官方容器（私有 MySQL8），授予平台 PG 只读账号；预置并导入两个大屏——「数据资产总览」（资产规模/域分布/热力/质量趋势）与「多模态处理监控」（吞吐量/脱敏命中/任务阶段/标注进度）；门户"可视化大屏"iframe 嵌入，经 `/bi/**` 网关代理（JWT 校验）；同时自研工作台保留 ECharts 核心 KPI 卡片，保证不启用 bi profile 时门户仍有概览。
6. **权限与流转（iam+gateway）**：网关 JWT/RBAC 粗鉴权（Redis 短缓存）、API-Key 开放接口、限流、统一审计；服务层 AuthzClient 调 iam 做 ABAC 细粒度判定；采集/处理/共享/应用流转状态随事件贯穿，全量操作入 audit_logs 可检索；等保三级控制项：BCrypt、JWT 过期刷新、内部令牌、密级标记、脱敏、密钥环境变量化、全审计。
7. **统一门户**：单点登录后按所启用 profile 动态显示菜单（标注/大屏入口在对应 profile 未启用时隐藏），外部系统页面无感知嵌入，不暴露其独立端口。

## 七、实施步骤（依赖顺序）

0. **前置镜像验证**：先实测 `heartexlabs/label-studio`、DataEase v2 官方镜像、AllData 官方镜像在本机的拉取与启动；镜像不可达则切换备用镜像源/代理。**此步结果决定 label/bi profile 是否可交付，最早执行。**
1. 工程骨架与基础设施：compose（profiles/健康检查/依赖顺序）、PG 五库初始化、MinIO 桶、Nacos、Kafka、Redis、ES；`.env.example`。
2. platform-common：统一响应/异常/JWT/内部令牌/Kafka/Redis/ES/MyBatis 配置、AuthzClient、审计发送。
3. iam-service：用户/角色/权限/APIKey CRUD、登录刷新、RBAC 判定内部接口、ABAC 条件树引擎、审计消费与查询、管理员初始化。
4. gateway：Nacos lb 路由、JWT 过滤器、RBAC 粗鉴权、APIKey、限流、审计；LS/BI 反向代理路由与令牌注入过滤器；CORS。
5. processing-service：模型/鉴权/MinIO、上传与任务 API、三模态流水线、Kafka 生产/消费 worker。
6. processing × Label Studio 对接：LS 容器（label profile、PG 库、禁公开注册）、ls_client 建项目/任务、worker 回拉标注、网关代理。
7. governance-service：数据源/元数据采集、统一资产目录、标签/标准、lifecycle 消费（入库+血缘+统计+ES 索引）、ES/PG 检索、热力图、血缘影响分析、质量规则与结果、资产开放 API。
8. dataset-service：数据集 CRUD、条件选数建版、质检引擎评分建议、版本管理/对比。
9. DataEase 集成：bi profile（MySQL8+官方容器+PG 只读账号）、两个预置大屏导入、网关代理、门户 iframe 嵌入。
10. 前端门户：脚手架/登录守卫/拦截器；工作台、多模态、标注、治理（目录/详情/血缘/热力/标准/质量）、数据集与报告、大屏中心、用户角色/ABAC/APIKey、审计、开放 API 页；按 profile 动态菜单。
11. 端到端联调与冒烟：`docker compose --profile label --profile bi up -d`；脚本验证全链路。
12. gov-full（可选，二期）：AllData 官方栈 override 接入、独立 Eureka/MySQL、网关跳转聚合与账号映射说明，不纳入 MVP 必验项。

## 八、依赖与注意事项

- 服务无状态、Kafka 消费组可扩多副本、Nacos lb 负载均衡，构成横向扩展基础；中间件 MVP 为单节点，生产需切换托管/多副本。
- Java 与 Python 服务均多阶段镜像构建，本机只需 Docker Desktop；ES 单节点限内存 512m。
- **内存预算（分级）**：core ≈ 6GB；core+label ≈ 7GB；core+label+bi ≈ 9–10GB；+gov-full 需 ≥16GB。README 级启动说明在最终交付回复中给出。
- DataEase 仅认 MySQL，作为其私有库独立容器；平台数据用 PG，DataEase 以 PG 只读连接取数。
- Label Studio 数据表由其自管，平台只持有服务令牌、不碰其库表，升级互不影响。
- 所有密码/令牌经 `.env` 注入；compose 不含真实密钥。

## 九、验证方案

1. `docker compose config` 校验；core 全部 healthy，Nacos 见三个 Java 服务。
2. 冒烟脚本（经网关端到端）：管理员登录 → 上传含手机/身份证文本 → 五阶段任务完成且脱敏命中 → 资产目录自动入库、ES 检索命中、血缘"原始→成品"可见、热力图有计数 → 创建数据集生成版本与三维度质量报告评分 → 低权限用户越权 403 → API-Key 调开放接口成功 → 审计日志可检索全部动作。
3. 图像（EXIF 剥离）、音频（元数据剥离）各跑一条；处理文件在 Label Studio 自动生成任务，人工标注后 worker 回拉成功，门户内嵌标注页可打开。
4. DataEase：bi profile 启动后，经网关 `/bi/` 登录态可访问，两个预置大屏正常出数；未携带平台 JWT 直接访问 `/bi/` 被网关拦截。
5. worker 双副本验证消费组负载均衡；关停 ES 验证资产检索自动降级 PG。

## 十、风险与应对

- **GPL-3.0 传染（AllData、DataEase）**：平台以独立容器+网络接口集成，不拷贝/不修改其源码，自研治理服务仅参考其模块设计与交互；若未来对外分发包体，需法务确认聚合合规或采购商业授权。风险接受并在交付说明列明。
- **镜像可达性/体积**：步骤 0 先验证；AllData 镜像与组件最大，故仅在 gov-full 可选 profile，不阻塞 MVP。
- **整机内存压力**：profile 分级 + ES 可停（检索降级）+ 健康检查与依赖顺序；bi/gov-full 默认不启。
- **Label Studio 社区版无 RBAC**：全部访问经网关收口，服务账号令牌代理，真实操作人记入平台审计；需要完整工作流/SSO 时升级企业版。
- **DataEase 深度免登（OIDC）需额外配置**：MVP 用网关代理+受控外链/iframe 保证只在登录后可见；二期对接 DataEase OIDC/LDAP 与自研 IAM 单点。
- **两套注册体系（Nacos vs AllData 的 Eureka）**：不做服务互注册，仅网关路由跳转/反代聚合，避免治理域故障影响核心链路。
- **质量指标口径**：多模态以元数据完整性+格式一致性+规则错误率为准并在报告展示口径；结构化质量走规则引擎；复杂抽样质检留扩展点。
- **等保三级为体系化要求**：本方案交付技术控制项（鉴权/审计/脱敏/密级/密钥管理/传输加密说明），制度、物理与运维侧要求需企业配套，最终说明列明差距。
