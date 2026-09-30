# API-TRAJ-002 模拟器控制接口规格

> 版本：v1.0 ｜ 日期：2026-09-30
> 所属模块：TRAJ
> 基础路径：/api/traj/simulator（外部控制）、/api/traj/ingest（数据接入）

---

## 1. 概述

### 1.1 认证方式

- **模拟器控制接口**（/simulator/*）：需 JWT Bearer Token + `traj:admin` 权限
- **数据接入接口**（/ingest/*）：需 `X-API-Key` 头（模拟器专用 API Key，不依赖 JWT）

### 1.2 通用请求头

| 头名 | 必填 | 说明 |
|---|---|---|
| Authorization | 控制接口必填 | `Bearer <JWT>` |
| X-API-Key | 接入接口必填 | 模拟器 API Key（配置项 `traj.simulator.api-key`） |
| Content-Type | POST 必填 | `application/json` |

### 1.3 通用响应体格式

```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

---

## 2. 接口清单

| 序号 | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 1 | POST | /api/traj/ingest | 单点接入（实时推送） | X-API-Key |
| 2 | POST | /api/traj/ingest/batch | 批量接入（批量导入） | X-API-Key |
| 3 | POST | /api/traj/simulator/start | 启动模拟器 | traj:admin |
| 4 | POST | /api/traj/simulator/stop | 停止模拟器 | traj:admin |
| 5 | GET | /api/traj/simulator/status | 查询模拟器状态 | traj:view |
| 6 | POST | /api/traj/simulator/batch-import | 触发批量导入 | traj:admin |

---

## 3. 接口详情

### 3.1 单点接入

- **方法**：POST
- **路径**：/api/traj/ingest
- **权限**：X-API-Key 校验
- **描述**：接收模拟器推送的单个轨迹点，校验并写入数据库

#### 请求

- **请求头**：

| 头名 | 必填 | 说明 |
|---|---|---|
| X-API-Key | 是 | 模拟器 API Key |
| Content-Type | 是 | application/json |

- **请求体**：

```json
{
  "identityCode": "TERM_001",
  "plateNo": "京A12345",
  "gpsTime": "2026-09-30T08:00:01",
  "lng": 116.407526,
  "lat": 39.904030,
  "speed": 42,
  "direction": 90,
  "altitude": 50,
  "alarmFlag": 0,
  "mileage": 12.35
}
```

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|---|---|---|---|---|
| identityCode | string | 是 | 非空，需在 traj_terminal 登记 | 设备号 |
| plateNo | string | 否 | — | 车牌号 |
| gpsTime | string | 是 | ISO 8601，≤ now() | GPS时间 |
| lng | double | 是 | 73.0 ~ 136.0 | 经度 |
| lat | double | 是 | 3.0 ~ 54.0 | 纬度 |
| speed | integer | 否 | ≥ 0 | 速度 km/h |
| direction | integer | 否 | 0 ~ 359 | 方向角 |
| altitude | integer | 否 | — | 海拔 米 |
| alarmFlag | integer | 否 | 0 或 1，默认 0 | 报警标志 |
| mileage | double | 否 | ≥ 0 | 累计里程 km |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 144001,
    "receiveTime": "2026-09-30T08:00:01"
  }
}
```

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 400 | TRAJ_001 | 设备号不存在 | identityCode 未登记 |
| 400 | TRAJ_002 | 经纬度超出中国范围 | lng/lat 越界 |
| 400 | TRAJ_003 | GPS时间超前于当前时间 | gpsTime > now() |
| 401 | — | API Key 无效 | X-API-Key 缺失或错误 |

#### 示例

```bash
curl -X POST "http://localhost:8080/api/traj/ingest" \
  -H "X-API-Key: sim-key-xxxxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "identityCode": "TERM_001",
    "plateNo": "京A12345",
    "gpsTime": "2026-09-30T08:00:01",
    "lng": 116.407526,
    "lat": 39.904030,
    "speed": 42,
    "direction": 90,
    "altitude": 50,
    "alarmFlag": 0,
    "mileage": 12.35
  }'
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.2 批量接入

- **方法**：POST
- **路径**：/api/traj/ingest/batch
- **权限**：X-API-Key 校验
- **描述**：接收模拟器批量推送的轨迹点列表，每批最多 500 条

#### 请求

- **请求体**：

```json
{
  "points": [
    {
      "identityCode": "TERM_001",
      "plateNo": "京A12345",
      "gpsTime": "2026-09-30T08:00:00",
      "lng": 116.407526,
      "lat": 39.904030,
      "speed": 0,
      "direction": 90,
      "altitude": 50,
      "alarmFlag": 0,
      "mileage": 12.34
    },
    {
      "identityCode": "TERM_001",
      "plateNo": "京A12345",
      "gpsTime": "2026-09-30T08:00:01",
      "lng": 116.407626,
      "lat": 39.904030,
      "speed": 42,
      "direction": 90,
      "altitude": 50,
      "alarmFlag": 0,
      "mileage": 12.35
    }
  ]
}
```

| 字段 | 类型 | 必填 | 校验规则 | 说明 |
|---|---|---|---|---|
| points | array | 是 | 1 ~ 500 条 | 轨迹点列表 |
| points[].* | — | — | 同 3.1 单点字段 | 字段定义同单点接入 |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "count": 2,
    "failed": 0,
    "errors": []
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| count | integer | 成功写入数量 |
| failed | integer | 失败数量 |
| errors | array | 失败详情列表（空表示全部成功） |
| errors[].index | integer | 失败点在数组中的索引 |
| errors[].reason | string | 失败原因 |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 400 | — | 批量数据不能超过500条 | points.length > 500 |
| 401 | — | API Key 无效 | X-API-Key 错误 |

#### 示例

```bash
curl -X POST "http://localhost:8080/api/traj/ingest/batch" \
  -H "X-API-Key: sim-key-xxxxxx" \
  -H "Content-Type: application/json" \
  -d '{"points":[{"identityCode":"TERM_001","gpsTime":"2026-09-30T08:00:00","lng":116.407526,"lat":39.904030,"speed":0}]}'
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.3 启动模拟器

- **方法**：POST
- **路径**：/api/traj/simulator/start
- **权限**：traj:admin
- **描述**：启动 Python 模拟器，按参数生成轨迹数据并推送

#### 请求

- **请求体**：

```json
{
  "vehicles": 5,
  "durationHours": 8,
  "frequencyHz": 1.0,
  "avgSpeedKmh": 40,
  "mode": "realtime"
}
```

| 字段 | 类型 | 必填 | 默认 | 校验规则 | 说明 |
|---|---|---|---|---|---|
| vehicles | integer | 否 | 5 | 1 ~ 20 | 模拟车辆数 |
| durationHours | integer | 否 | 8 | 1 ~ 24 | 模拟时长（小时） |
| frequencyHz | double | 否 | 1.0 | 0.5 ~ 5.0 | 采样频率 Hz |
| avgSpeedKmh | double | 否 | 40.0 | 20 ~ 80 | 平均速度 km/h |
| mode | string | 否 | realtime | realtime / batch | 推送模式 |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "sim-20260930-001",
    "status": "running",
    "config": {
      "vehicles": 5,
      "durationHours": 8,
      "frequencyHz": 1.0,
      "mode": "realtime"
    },
    "startTime": "2026-09-30T08:00:00"
  }
}
```

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 409 | TRAJ_005 | 模拟器已运行，请先停止 | 重复启动 |
| 503 | TRAJ_008 | 模拟器服务不可达 | Python 进程未启动 |
| 403 | IAM_403 | 权限不足 | 无 traj:admin 权限 |

#### 示例

```bash
curl -X POST "http://localhost:8080/api/traj/simulator/start" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{"vehicles":5,"durationHours":8,"frequencyHz":1.0,"mode":"realtime"}'
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.4 停止模拟器

- **方法**：POST
- **路径**：/api/traj/simulator/stop
- **权限**：traj:admin
- **描述**：停止正在运行的模拟器

#### 请求

无请求体。

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "sim-20260930-001",
    "status": "stopped",
    "stopTime": "2026-09-30T09:00:00",
    "pointsGenerated": 144000
  }
}
```

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 409 | TRAJ_004 | 模拟器未运行 | 状态非 running |
| 503 | TRAJ_008 | 模拟器服务不可达 | Python 进程无响应 |

#### 示例

```bash
curl -X POST "http://localhost:8080/api/traj/simulator/stop" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.5 查询模拟器状态

- **方法**：GET
- **路径**：/api/traj/simulator/status
- **权限**：traj:view
- **描述**：查询模拟器当前运行状态

#### 请求

无查询参数。

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "running",
    "taskId": "sim-20260930-001",
    "vehicles": 5,
    "pointsGenerated": 144000,
    "uptime": "02:00:00",
    "startTime": "2026-09-30T08:00:00",
    "config": {
      "vehicles": 5,
      "durationHours": 8,
      "frequencyHz": 1.0,
      "mode": "realtime"
    }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| status | string | 状态：running / stopped / error |
| taskId | string | 任务ID |
| vehicles | integer | 当前模拟车辆数 |
| pointsGenerated | long | 已生成轨迹点总数 |
| uptime | string | 运行时长 HH:mm:ss |
| startTime | string | 启动时间 |
| config | object | 启动时的配置参数 |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 503 | TRAJ_008 | 模拟器服务不可达 | Python 进程无响应 |

#### 示例

```bash
curl -X GET "http://localhost:8080/api/traj/simulator/status" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.6 触发批量导入

- **方法**：POST
- **路径**：/api/traj/simulator/batch-import
- **权限**：traj:admin
- **描述**：触发模拟器一次性生成并批量导入完整时段的轨迹数据

#### 请求

- **请求体**：

```json
{
  "vehicles": 5,
  "durationHours": 8,
  "frequencyHz": 1.0,
  "avgSpeedKmh": 40
}
```

| 字段 | 类型 | 必填 | 默认 | 校验规则 | 说明 |
|---|---|---|---|---|---|
| vehicles | integer | 否 | 5 | 1 ~ 20 | 模拟车辆数 |
| durationHours | integer | 否 | 8 | 1 ~ 24 | 模拟时长 |
| frequencyHz | double | 否 | 1.0 | 0.5 ~ 5.0 | 采样频率 |
| avgSpeedKmh | double | 否 | 40.0 | 20 ~ 80 | 平均速度 |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "batch-20260930-001",
    "status": "completed",
    "totalPoints": 144000,
    "importedPoints": 144000,
    "failedPoints": 0,
    "duration": "00:00:25"
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| totalPoints | long | 生成轨迹点总数 |
| importedPoints | long | 成功导入数量 |
| failedPoints | long | 失败数量 |
| duration | string | 导入耗时 HH:mm:ss |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 503 | TRAJ_008 | 模拟器服务不可达 | Python 进程无响应 |
| 403 | IAM_403 | 权限不足 | 无 traj:admin 权限 |

#### 示例

```bash
curl -X POST "http://localhost:8080/api/traj/simulator/batch-import" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -H "Content-Type: application/json" \
  -d '{"vehicles":5,"durationHours":8,"frequencyHz":1.0}'
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

## 4. 错误码

| code | HTTP | message | 场景 |
|---|---|---|---|
| TRAJ_001 | 400 | 设备号不存在 | identityCode 未登记 |
| TRAJ_002 | 400 | 经纬度超出中国范围 | 坐标越界 |
| TRAJ_003 | 400 | GPS时间超前于当前时间 | 时间戳在未来 |
| TRAJ_004 | 409 | 模拟器未运行 | stop/status 时未运行 |
| TRAJ_005 | 409 | 模拟器已运行 | 重复 start |
| TRAJ_008 | 503 | 模拟器服务不可达 | Python 进程无响应 |
| IAM_403 | 403 | 权限不足 | 无对应权限码 |

---

## 5. 修订记录

| 版本 | 日期 | 修订人 | 修订内容 |
|---|---|---|---|
| v1.0 | 2026-09-30 | system | 初版：6 个模拟器控制与数据接入接口 |
