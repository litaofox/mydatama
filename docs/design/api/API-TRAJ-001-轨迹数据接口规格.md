# API-TRAJ-001 轨迹数据接口规格

> 版本：v1.0 ｜ 日期：2026-09-30
> 所属模块：TRAJ
> 基础路径：/api/traj

---

## 1. 概述

### 1.1 认证方式

所有接口均需携带 `Authorization: Bearer <JWT>` 请求头，JWT 由 IAM 模块签发。模拟器数据接入接口 additionally 需携带 `X-API-Key` 头。

### 1.2 通用请求头

| 头名 | 必填 | 说明 |
|---|---|---|
| Authorization | 是 | `Bearer <JWT>` |
| X-API-Key | 仅接入接口 | 模拟器专用 API Key |
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
| code | integer | 0=成功，非0=失败 |
| message | string | 描述信息 |
| data | object/array | 业务数据 |

---

## 2. 接口清单

| 序号 | 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|---|
| 1 | GET | /api/traj/query | 分页查询轨迹点 | traj:query |
| 2 | GET | /api/traj/replay | 获取回放轨迹 | traj:query |
| 3 | GET | /api/traj/metrics | 获取轨迹指标 | traj:query |
| 4 | GET | /api/traj/export | 导出轨迹数据 | traj:export |
| 5 | GET | /api/traj/dashboard/overview | 大屏总览统计 | traj:view |
| 6 | GET | /api/traj/dashboard/realtime | 大屏实时位置 | traj:view |
| 7 | GET | /api/traj/dashboard/alarms | 大屏报警统计 | traj:view |
| 8 | GET | /api/traj/dashboard/hourly | 大屏时段分布 | traj:view |

---

## 3. 接口详情

### 3.1 分页查询轨迹点

- **方法**：GET
- **路径**：/api/traj/query
- **权限**：traj:query
- **描述**：按设备号/车牌/时间范围/报警标志分页查询轨迹点

#### 请求

- **查询参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| identityCode | string | 否 | — | 设备号 |
| plateNo | string | 否 | — | 车牌号 |
| startTime | string | 否 | — | 起始时间（ISO 8601: 2026-09-30T08:00:00） |
| endTime | string | 否 | — | 结束时间 |
| alarmOnly | boolean | 否 | false | 仅查询报警点 |
| bbox | string | 否 | — | 地图视野边界 minLng,minLat,maxLng,maxLat |
| page | integer | 否 | 0 | 页码（从0开始） |
| size | integer | 否 | 20 | 每页条数（最大100） |
| sort | string | 否 | gps_time,desc | 排序字段,方向 |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 144001,
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
    ],
    "totalElements": 3600,
    "totalPages": 180,
    "number": 0,
    "size": 20
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| content | array | 轨迹点列表 |
| content[].id | long | 记录ID |
| content[].identityCode | string | 设备号 |
| content[].plateNo | string | 车牌号 |
| content[].gpsTime | string | GPS时间 |
| content[].lng | double | 经度 |
| content[].lat | double | 纬度 |
| content[].speed | integer | 速度 km/h |
| content[].direction | integer | 方向角 0-359 |
| content[].altitude | integer | 海拔 米 |
| content[].alarmFlag | integer | 报警标志 0=正常 1=报警 |
| content[].mileage | double | 累计里程 km |
| totalElements | long | 总记录数 |
| totalPages | integer | 总页数 |
| number | integer | 当前页码 |
| size | integer | 每页条数 |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 400 | TRAJ_006 | 查询时间范围不能超过30天 | 时间跨度>30天 |
| 403 | IAM_403 | 权限不足 | 无 traj:query 权限 |

#### 示例

```bash
curl -X GET "http://localhost:8080/api/traj/query?identityCode=TERM_001&startTime=2026-09-30T08:00:00&endTime=2026-09-30T09:00:00&page=0&size=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.2 获取回放轨迹

- **方法**：GET
- **路径**：/api/traj/replay
- **权限**：traj:query
- **描述**：获取指定设备在时间范围内的完整轨迹序列（按时间升序），超量时自动抽样

#### 请求

- **查询参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| identityCode | string | 是 | — | 设备号 |
| startTime | string | 是 | — | 起始时间 |
| endTime | string | 是 | — | 结束时间 |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "identityCode": "TERM_001",
    "plateNo": "京A12345",
    "startTime": "2026-09-30T08:00:00",
    "endTime": "2026-09-30T09:00:00",
    "total": 3600,
    "sampled": false,
    "points": [
      {
        "time": "2026-09-30T08:00:00",
        "lng": 116.407526,
        "lat": 39.904030,
        "speed": 0,
        "direction": 90,
        "alarmFlag": 0
      },
      {
        "time": "2026-09-30T08:00:01",
        "lng": 116.407626,
        "lat": 39.904030,
        "speed": 42,
        "direction": 90,
        "alarmFlag": 0
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| total | integer | 原始轨迹点总数 |
| sampled | boolean | 是否已抽样 |
| points | array | 轨迹点列表（按时间升序） |
| points[].time | string | GPS时间 |
| points[].lng | double | 经度 |
| points[].lat | double | 纬度 |
| points[].speed | integer | 速度 km/h |
| points[].direction | integer | 方向角 |
| points[].alarmFlag | integer | 报警标志 |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 400 | TRAJ_006 | 查询时间范围不能超过30天 | 时间跨度>30天 |
| 400 | TRAJ_001 | 设备号不存在 | identityCode 未登记 |

#### 示例

```bash
curl -X GET "http://localhost:8080/api/traj/replay?identityCode=TERM_001&startTime=2026-09-30T08:00:00&endTime=2026-09-30T09:00:00" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.3 获取轨迹指标

- **方法**：GET
- **路径**：/api/traj/metrics
- **权限**：traj:query
- **描述**：计算指定设备在时间范围内的行驶指标（总距离、平均速度、最高速度、停留点）

#### 请求

- **查询参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| identityCode | string | 是 | — | 设备号 |
| startTime | string | 是 | — | 起始时间 |
| endTime | string | 是 | — | 结束时间 |

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "identityCode": "TERM_001",
    "plateNo": "京A12345",
    "startTime": "2026-09-30T08:00:00",
    "endTime": "2026-09-30T09:00:00",
    "totalDistance": 35.6,
    "duration": 3600,
    "avgSpeed": 35.6,
    "maxSpeed": 68,
    "stopCount": 3,
    "stopPoints": [
      {
        "lng": 116.408000,
        "lat": 39.905000,
        "startTime": "2026-09-30T08:15:00",
        "endTime": "2026-09-30T08:15:45",
        "duration": 45
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| totalDistance | double | 总行驶距离 km |
| duration | integer | 总时长 秒 |
| avgSpeed | double | 平均速度 km/h |
| maxSpeed | integer | 最高速度 km/h |
| stopCount | integer | 停留点数量 |
| stopPoints | array | 停留点列表 |
| stopPoints[].lng | double | 停留经度 |
| stopPoints[].lat | double | 停留纬度 |
| stopPoints[].startTime | string | 停留开始时间 |
| stopPoints[].endTime | string | 停留结束时间 |
| stopPoints[].duration | integer | 停留时长 秒 |

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 400 | TRAJ_006 | 查询时间范围不能超过30天 | 时间跨度>30天 |

#### 示例

```bash
curl -X GET "http://localhost:8080/api/traj/metrics?identityCode=TERM_001&startTime=2026-09-30T08:00:00&endTime=2026-09-30T09:00:00" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.4 导出轨迹数据

- **方法**：GET
- **路径**：/api/traj/export
- **权限**：traj:export
- **描述**：导出符合条件的轨迹数据为 CSV 或 Excel 文件

#### 请求

- **查询参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| identityCode | string | 否 | — | 设备号 |
| plateNo | string | 否 | — | 车牌号 |
| startTime | string | 否 | — | 起始时间 |
| endTime | string | 否 | — | 结束时间 |
| format | string | 否 | csv | 导出格式：csv 或 xlsx |

#### 响应

- **成功**（200）：
  - Content-Type: `text/csv` 或 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - Content-Disposition: `attachment; filename="traj_TERM_001_20260930.csv"`
  - 响应体为文件流

- **错误响应**：

| HTTP 状态码 | code | message | 场景 |
|---|---|---|---|
| 400 | TRAJ_006 | 查询时间范围不能超过30天 | 时间跨度>30天 |
| 400 | — | 导出数据量过大(>10万行) | 记录数>100000 |
| 403 | IAM_403 | 权限不足 | 无 traj:export 权限 |

#### 示例

```bash
curl -X GET "http://localhost:8080/api/traj/export?identityCode=TERM_001&startTime=2026-09-30T08:00:00&endTime=2026-09-30T09:00:00&format=csv" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -o traj_TERM_001_20260930.csv
```

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.5 大屏总览统计

- **方法**：GET
- **路径**：/api/traj/dashboard/overview
- **权限**：traj:view
- **描述**：获取大屏顶部 4 个统计卡片数据（缓存 10 秒）

#### 请求

无查询参数。

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "vehicleTotal": 5,
    "onlineCount": 5,
    "todayPoints": 144000,
    "pendingAlarms": 3
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| vehicleTotal | integer | 车辆总数 |
| onlineCount | integer | 在线车辆数（最近5分钟有数据） |
| todayPoints | long | 今日轨迹点总数 |
| pendingAlarms | integer | 待处理报警数 |

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.6 大屏实时位置

- **方法**：GET
- **路径**：/api/traj/dashboard/realtime
- **权限**：traj:view
- **描述**：获取所有车辆最新位置（每5秒轮询）

#### 请求

无查询参数。

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "positions": [
      {
        "identityCode": "TERM_001",
        "plateNo": "京A12345",
        "lng": 116.407526,
        "lat": 39.904030,
        "speed": 42,
        "direction": 90,
        "gpsTime": "2026-09-30T08:30:01",
        "alarmFlag": 0,
        "online": true
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| positions | array | 车辆位置列表 |
| positions[].identityCode | string | 设备号 |
| positions[].plateNo | string | 车牌号 |
| positions[].lng | double | 经度 |
| positions[].lat | double | 纬度 |
| positions[].speed | integer | 速度 km/h |
| positions[].direction | integer | 方向角 |
| positions[].gpsTime | string | 最新GPS时间 |
| positions[].alarmFlag | integer | 报警标志 |
| positions[].online | boolean | 是否在线（5分钟内有数据） |

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.7 大屏报警统计

- **方法**：GET
- **路径**：/api/traj/dashboard/alarms
- **权限**：traj:view
- **描述**：获取今日报警类型分布（饼图数据）

#### 请求

无查询参数。

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      { "name": "超速报警", "value": 12 },
      { "name": "疲劳驾驶", "value": 8 },
      { "name": "围栏越界", "value": 5 },
      { "name": "紧急求助", "value": 2 }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 报警类型分布 |
| items[].name | string | 报警类型名称 |
| items[].value | integer | 今日报警次数 |

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

### 3.8 大屏时段分布

- **方法**：GET
- **路径**：/api/traj/dashboard/hourly
- **权限**：traj:view
- **描述**：获取今日 24 小时轨迹点数分布（柱图数据）

#### 请求

无查询参数。

#### 响应

- **成功**（200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      { "hour": 0, "count": 0 },
      { "hour": 1, "count": 0 },
      { "hour": 8, "count": 18000 },
      { "hour": 9, "count": 18000 }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| items | array | 24小时分布 |
| items[].hour | integer | 小时（0-23） |
| items[].count | long | 该小时轨迹点数 |

#### 变更记录

| 版本 | 变更内容 |
|---|---|
| v1.0 | 初版 |

---

## 4. 错误码

| code | HTTP | message | 场景 |
|---|---|---|---|
| TRAJ_001 | 400 | 设备号不存在 | identityCode 未在 traj_terminal 登记 |
| TRAJ_002 | 400 | 经纬度超出中国范围 | 坐标越界 |
| TRAJ_003 | 400 | GPS时间超前于当前时间 | 时间戳在未来 |
| TRAJ_006 | 400 | 查询时间范围不能超过30天 | 查询跨度>30天 |
| TRAJ_007 | 200 | 回放数据已抽样 | 原始点数>5000 |
| IAM_403 | 403 | 权限不足 | 无对应权限码 |

---

## 5. 修订记录

| 版本 | 日期 | 修订人 | 修订内容 |
|---|---|---|---|
| v1.0 | 2026-09-30 | system | 初版：8 个轨迹数据接口 |
