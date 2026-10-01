# API-COM-001 内部接口规格

> 版本：v1.0 ｜ 日期：2026-09-30
> 所属模块：COM（公共基础）
> 基础路径：/internal
> 关联文档：MOD-COM-001, SYS-003, SYS-004

---

## 1. 概述

### 1.1 认证方式

内部接口仅允许平台内部容器间调用，采用**服务令牌**认证：

- 请求头携带 `X-Service-Token`，值由部署环境变量 `INTERNAL_SERVICE_TOKEN` 配置，不对外暴露。
- **禁止**与用户 JWT 混用；Python 侧调用内部接口时单独持有该令牌。
- Nginx 不代理 `/internal/**` 路径；内部接口仅绑定容器内部网络（`0.0.0.0` 但外部不可达）。

### 1.2 通用请求头

| 头名 | 必填 | 说明 |
|---|---|---|
| X-Service-Token | 是 | 服务间调用令牌（环境变量配置，24 字节以上随机串） |
| Content-Type | POST/PUT 必填 | `application/json` |

### 1.3 通用响应体格式

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | integer | 0 = 成功，非 0 = 失败（业务错误码） |
| message | string | 描述信息 |
| data | object / null | 业务数据，失败时通常为 null |

---

## 2. 接口清单

| 序号 | 方法 | 路径 | 说明 | 调用方 |
|---|---|---|---|---|
| 1 | POST | /internal/assets/register | 资产登记（处理完成后回调） | processing-app |
| 2 | POST | /internal/audit | 审计日志上报（Python 侧关键动作） | processing-app / Label Studio 代理 |
| 3 | POST | /internal/authz | 细粒度鉴权回调（ABAC 条件求值） | processing-app |

---

## 3. 接口详情

### 3.1 资产登记

- **方法**：POST
- **路径**：/internal/assets/register
- **调用方**：processing-app（Python 处理服务）
- **描述**：processing-app 完成文件五阶段流水线处理后，回调 Java 侧登记资产入库、写入血缘边、更新使用统计。支持幂等重入。

#### 请求

- **Content-Type**：`application/json`
- **请求头**：

| 头名 | 必填 | 说明 |
|---|---|---|
| X-Service-Token | 是 | 服务间调用令牌 |
| Content-Type | 是 | `application/json` |

- **请求体**：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|---|---|---|---|---|
| sourceFileId | long | 是 | > 0 | proc.data_files.id，作为幂等键 |
| fileName | string | 是 | 非空，≤255 字符 | 原始文件名 |
| modality | string | 是 | 枚举：STRUCTURED / TEXT / IMAGE / VIDEO | 数据模态 |
| format | string | 是 | 非空，≤32 字符 | 文件格式（csv / json / xlsx / txt / jpg / png / mp4 等） |
| bizDomain | string | 是 | 非空，≤64 字符 | 业务域标签 |
| secretLevel | int | 是 | 0–4 | 密级（0 公开，4 绝密） |
| sizeBytes | long | 否 | ≥0 | 文件大小（字节） |
| rawPath | string | 是 | 非空，≤512 | 原始文件存储路径（/data/raw/...） |
| processedPath | string | 否 | ≤512 | 处理后文件路径 |
| thumbPath | string | 否 | ≤512 | 缩略图路径（图像/视频） |
| metrics | object | 是 | — | 处理指标 JSON，结构见下方 |
| columns | array | 否 | — | 结构化文件列元数据，见下方 |
| lineage | object | 是 | — | 血缘信息，见下方 |

**metrics 结构**：

```json
{
  "rowCount": 12000,
  "columnCount": 8,
  "missingRate": 0.002,
  "duplicateRate": 0.001,
  "formatConsistencyRate": 0.998,
  "sensitiveColumnCount": 3,
  "errorRowCount": 0,
  "charCount": 0,
  "validLineRate": 0,
  "sensitiveHitCount": 0,
  "resolution": "1920x1080",
  "hasExif": false,
  "durationSec": 0,
  "bitrateKbps": 0
}
```

> 各模态指标按实际处理产出填充；非本模态字段可省略或置 0。

**columns 结构**（仅结构化文件必填）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| colName | string | 是 | 列名 |
| dataType | string | 是 | 推断数据类型（STRING / INT / FLOAT / DATE / BOOLEAN） |
| sensitive | boolean | 否 | 是否为敏感列 |
| maskStrategy | string | 否 | 脱敏策略（MASK / HASH / NULL） |

**lineage 结构**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| parentAssetId | long | 否 | 上游资产 ID（raw→processed 时填 raw 的资产 ID） |
| relType | string | 是 | 枚举：DERIVED_FROM / ANNOTATED_BY / REFERENCED_BY |
| description | string | 否 | 关系描述 |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "assetId": 1042
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| assetId | long | 已登记或已存在的资产 ID |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 401 | 10001 | Invalid service token | X-Service-Token 缺失或错误 |
| 409 | 10002 | Asset already registered | 同一 sourceFileId 已存在且状态为就绪 |
| 400 | 10003 | Invalid modality | modality 不在枚举范围 |
| 500 | 19999 | Internal server error | 服务内部异常 |

#### 示例

- **请求示例**：

```bash
curl -X POST http://platform-app:8080/internal/assets/register \
  -H "X-Service-Token: ${INTERNAL_SERVICE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceFileId": 1001,
    "fileName": "customer_2026.csv",
    "modality": "STRUCTURED",
    "format": "csv",
    "bizDomain": "零售",
    "secretLevel": 2,
    "sizeBytes": 524288,
    "rawPath": "/data/raw/customer_2026.csv",
    "processedPath": "/data/processed/customer_2026_processed.csv",
    "metrics": {
      "rowCount": 12000,
      "columnCount": 8,
      "missingRate": 0.002,
      "duplicateRate": 0.001,
      "formatConsistencyRate": 0.998,
      "sensitiveColumnCount": 3,
      "errorRowCount": 0
    },
    "columns": [
      {"colName": "user_id", "dataType": "STRING", "sensitive": false},
      {"colName": "phone", "dataType": "STRING", "sensitive": true, "maskStrategy": "MASK"},
      {"colName": "id_card", "dataType": "STRING", "sensitive": true, "maskStrategy": "HASH"},
      {"colName": "email", "dataType": "STRING", "sensitive": true, "maskStrategy": "MASK"},
      {"colName": "age", "dataType": "INT", "sensitive": false},
      {"colName": "city", "dataType": "STRING", "sensitive": false},
      {"colName": "purchase_amount", "dataType": "FLOAT", "sensitive": false},
      {"colName": "register_date", "dataType": "DATE", "sensitive": false}
    ],
    "lineage": {
      "relType": "DERIVED_FROM",
      "description": "从原始 CSV 处理后生成"
    }
  }'
```

- **响应示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "assetId": 1042
  }
}
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初始版本 |

---

### 3.2 审计日志上报

- **方法**：POST
- **路径**：/internal/audit
- **调用方**：processing-app / Label Studio 代理层
- **描述**：Python 侧处理动作（上传、重试、标注保存等）通过此接口上报 Java 审计系统，统一落入 `iam.audit_logs`。

#### 请求

- **Content-Type**：`application/json`
- **请求头**：

| 头名 | 必填 | 说明 |
|---|---|---|
| X-Service-Token | 是 | 服务间调用令牌 |
| Content-Type | 是 | `application/json` |

- **请求体**：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|---|---|---|---|---|
| username | string | 是 | 非空，≤64 | 平台用户账号 |
| action | string | 是 | 枚举：UPLOAD / RETRY / ANNOTATION_SAVE / ENTER_LABEL_STUDIO / MASK_APPLY / EXPORT | 动作标签 |
| resource | string | 否 | ≤255 | 操作对象描述（如文件 ID、任务 ID） |
| detail | object | 否 | — | 扩展详情 JSON（如文件大小、模态、失败原因） |
| ip | string | 否 | ≤45 | 用户真实 IP（Nginx 透传 X-Forwarded-For） |
| occurredAt | string | 否 | ISO 8601 | 动作发生时间；为空时取服务端接收时间 |

**detail 示例**：

```json
{
  "fileId": 1001,
  "fileName": "customer_2026.csv",
  "modality": "STRUCTURED",
  "fileSize": 524288
}
```

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "auditLogId": 88421
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| auditLogId | long | 已写入的审计日志 ID |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 401 | 10001 | Invalid service token | X-Service-Token 缺失或错误 |
| 400 | 10004 | Invalid action tag | action 不在枚举范围 |
| 500 | 19999 | Internal server error | 服务内部异常 |

#### 示例

- **请求示例**：

```bash
curl -X POST http://platform-app:8080/internal/audit \
  -H "X-Service-Token: ${INTERNAL_SERVICE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "action": "UPLOAD",
    "resource": "file:1001",
    "detail": {
      "fileId": 1001,
      "fileName": "customer_2026.csv",
      "modality": "STRUCTURED",
      "fileSize": 524288
    },
    "ip": "192.168.1.100",
    "occurredAt": "2026-09-30T14:22:08+08:00"
  }'
```

- **响应示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "auditLogId": 88421
  }
}
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初始版本 |

---

### 3.3 细粒度鉴权回调

- **方法**：POST
- **路径**：/internal/authz
- **调用方**：processing-app
- **描述**：Python 侧已做 JWT 验签与角色粗鉴权；当接口涉及密级、部门等 ABAC 细粒度条件时，将求值上下文回调 Java ABAC 引擎判定。

#### 请求

- **Content-Type**：`application/json`
- **请求头**：

| 头名 | 必填 | 说明 |
|---|---|---|
| X-Service-Token | 是 | 服务间调用令牌 |
| Content-Type | 是 | `application/json` |

- **请求体**：

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|---|---|---|---|---|
| username | string | 是 | 非空，≤64 | 当前请求用户账号 |
| permission | string | 是 | 格式：module:resource:action | 待校验的权限码 |
| attributes | object | 是 | — | ABAC 求值上下文，见下方 |

**attributes 结构**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| user.deptCode | string | 否 | 用户部门编码 |
| user.secretLevel | int | 否 | 用户密级 |
| user.roles | array[string] | 否 | 用户角色编码列表 |
| asset.secretLevel | int | 否 | 资源密级 |
| asset.ownerDept | string | 否 | 资源归属部门 |
| asset.bizDomain | string | 否 | 资源业务域 |
| env.clientIp | string | 否 | 客户端 IP |
| env.requestTime | string | 否 | 请求时间（ISO 8601） |

> 上述属性为常用示例；ABAC 条件树中引用的属性需全部传入，缺失属性视为 null。

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "allow": true,
    "reason": "ALLOW"
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| allow | boolean | true = 允许，false = 拒绝 |
| reason | string | 决策原因：ALLOW / DENY_BY_POLICY / DENY_NO_POLICY / DENY_ATTR_MISSING / DENY_EXCEPTION |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 401 | 10001 | Invalid service token | X-Service-Token 缺失或错误 |
| 400 | 10005 | Invalid permission format | permission 不符合 module:resource:action 格式 |
| 403 | 10006 | Access denied | RBAC 基础权限即被拒绝（无需进入 ABAC） |
| 500 | 19999 | Internal server error | ABAC 引擎求值异常 |

#### 示例

- **请求示例**：

```bash
curl -X POST http://platform-app:8080/internal/authz \
  -H "X-Service-Token: ${INTERNAL_SERVICE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "permission": "proc:file:download",
    "attributes": {
      "user.deptCode": "DEPT_001",
      "user.secretLevel": 2,
      "user.roles": ["ROLE_ANALYST"],
      "asset.secretLevel": 2,
      "asset.ownerDept": "DEPT_001",
      "asset.bizDomain": "零售",
      "env.clientIp": "192.168.1.100",
      "env.requestTime": "2026-09-30T14:22:08+08:00"
    }
  }'
```

- **响应示例（允许）**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "allow": true,
    "reason": "ALLOW"
  }
}
```

- **响应示例（拒绝）**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "allow": false,
    "reason": "DENY_BY_POLICY"
  }
}
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初始版本 |

---

## 4. 错误码

| code | HTTP | message | 场景 |
|---|---|---|---|
| 0 | 200 | success | 请求成功 |
| 10001 | 401 | Invalid service token | 服务令牌缺失或错误 |
| 10002 | 409 | Asset already registered | 资产登记幂等冲突 |
| 10003 | 400 | Invalid modality | modality 枚举值非法 |
| 10004 | 400 | Invalid action tag | 审计动作标签非法 |
| 10005 | 400 | Invalid permission format | 权限码格式错误 |
| 10006 | 403 | Access denied | RBAC 基础权限拒绝 |
| 19999 | 500 | Internal server error | 服务内部异常 |

> 错误码前缀 `1` 标识公共基础（COM）模块；与 SYS-004 系统异常处理设计中的错误码规范一致。

---

## 5. 修订记录

| 版本 | 日期 | 修订人 | 修订内容 |
|---|---|---|---|
| v1.0 | 2026-09-30 | — | 初始版本，定义 3 个内部接口规格 |
