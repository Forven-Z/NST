# 智慧云脑诊疗平台 — API 接口文档（唯一契约）

> **版本**：v2.3 | 2026-06-10  
> **地位**：**唯一** HTTP 接口规格；前后端、Mock、联调均以此为准。  
> **Base URL**：`http://{host}:9000/api/v1`（经 Gateway，禁止前端直连微服务端口）  
> **数据模型**：[DATABASE_DESIGN.md](./DATABASE_DESIGN.md) **v1.14**（业务 ID 即各表 `id`）  
> **关联**：[MICROSERVICES.md](./MICROSERVICES.md) · [DESIGN_DECISIONS.md](./DESIGN_DECISIONS.md)

---

## 〇、路径定稿（必读）

下文所有路径均相对于 **`/api/v1`**。完整 URL 示例：`POST /api/v1/patient/registers`。

### 0.1 定稿路径 vs 历史写法

| 定稿路径（**唯一标准**） | 历史/错误写法（**禁止新代码使用**） |
|--------------------------|--------------------------------------|
| `GET /doctor/queues` | `/registers/queue`、`/doctor/registers/queue` |
| `POST /doctor/call/{registerId}` | `POST /doctor/registers/{id}/call` |
| `GET/PUT /doctor/medical-records/{registerId}` | `/doctor/registers/{id}/medical-record` |
| `POST /doctor/medical-records/{registerId}/submit` | `/doctor/registers/{id}/medical-record/confirm`、`/submit` 混用 |
| `POST /doctor/registers/{registerId}/finish` | —（定稿保留 `registers` 段） |
| 医嘱结果：见 §5.5（**无**聚合 `/results`） | `GET /doctor/registers/{id}/results` |
| `GET /admin/scheduling` | `GET /admin/schedules` |
| `POST/PUT /admin/scheduling` | `POST/PUT /admin/schedules` |
| `POST /pharmacy/prescriptions/{id}/dispense` | `/pharmacy/dispense/{id}` |
| `POST /lis\|pacs\|disposal/requests/{id}/result` | `PUT .../result`（Method 错误） |
| `POST /patient/payments`（Mock 批量支付） | 首期演示用；真微信支付见 §4.3.2 预留 |
| 医技执行 | `GET/POST /lis/**`、`/pacs/**`、`/disposal/**`（非 `/doctor` 下执行） |
| 窗口号别 | 定稿待增 `GET /registrar/regist-levels`；暂勿长期依赖 `GET /admin/regist-levels` |

### 0.2 网关 → 微服务

| 路径前缀 | 服务 | 端口 |
|----------|------|------|
| `/auth/**` | hospital-auth | 9101 |
| `/patient/**` `/doctor/**` `/registrar/**` `/pharmacy/**` | hospital-his | 9102 |
| `/lis/**` | hospital-lis | 9103 |
| `/pacs/**` | hospital-pacs | 9104 |
| `/disposal/**` | hospital-disposal | 9105 |
| `/ai/**` | hospital-ai-bridge | 9106 |
| `/admin/**` | hospital-management | 9107 |
| `/callback/wechat/**` | hospital-his | 9102 |
| `/internal/**` | 各服务 | 不经 Gateway |

### 0.3 PC 前端（`hospital-frontend`）对齐说明

> 源码：`hospital-frontend/src/api/*.js` · 开关：`.env.development` → `VITE_USE_MOCK`  
> **定稿路径以 §0.1 为准**；下表说明当前前端封装与联调现状（2026-06-10 审计）。

| api 模块 | 页面/组件 | 定稿符合度 | 说明 |
|----------|-----------|------------|------|
| `auth.js` | 登录 | ✅ | `POST /auth/staff/login` |
| `doctor.js` | `WorkspaceView`、`RegisterOrdersPanel` | ⚠️ 部分 | 病历/叫号/orders/submit 已对齐；`finish` 后端 ⬜；医嘱结果见 §5.5 |
| `registrar.js` | 挂号/收费/退费 | ⚠️ | 核心路径 ✅；号别暂调 `GET /admin/regist-levels`（§8.1） |
| `lis.js` / `pacs.js` / `disposal.js` | `TechQueuePanel`、影像页 | ⚠️ | 队列/execute ✅；**保存结果 Method 应为 POST**（前端仍 PUT，见附录 G） |
| `pharmacy.js` | 待发药 | ✅ | |
| `admin.js` | 字典/员工/排班 | ✅ | 字典 GET ✅；科室/员工/排班 CRUD 路径 `/admin/scheduling/**` |
| `scheduling.js` | `MyScheduleView`、`SchedulingView` | 🎭 Mock | §8.5 / §9.5 整套接口仅 Mock，后端 ⬜ P5 |

**图例**：✅ 关 Mock 可联调 · ⚠️ 路径或字段待改 · 🎭 仅 Mock · ⬜ 契约已定后端未实现

### 0.4 前端待改项（对齐本契约）

完整清单见 **附录 G**。联调前优先：

1. ~~医技 `saveResult`：`PUT` → `POST`~~（`lis.js` / `pacs.js` / `disposal.js` 已 POST ✅）
2. ~~**PACS 对齐 LIS**：`result-detail` / `ai-report` STUB / `QueueView` 三段式~~（✅ 2026-06-11）
3. 管理端排班：`/admin/schedules` → `/admin/scheduling`（`admin.js`）✅
4. 挂号号别：改用 `GET /registrar/regist-levels`（后端待增）或文档批准的临时只读代理
5. `RegisterOrdersPanel`：status `30` 文案改为「执行中」（§1.7）
6. 废弃 `API_CONTRACT.md` 中历史路径，统一读本文件

---

## 一、通用约定

### 1.1 响应体 `Result<T>`

```json
{
  "code": 200,
  "message": "ok",
  "success": true,
  "data": { }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录 / Token 无效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 业务冲突 |
| 500 | 服务器错误 |
| 50301 | AI 未启用（STUB） |
| 50302 | CNN 未启用（STUB） |

**分页** `data`：`{ "list": [], "total": 100, "page": 1, "pageSize": 20 }`

### 1.2 请求头

| Header | 说明 |
|--------|------|
| `Authorization` | `Bearer {accessToken}`（白名单除外） |
| `X-Request-Id` | 可选，链路追踪 |
| `X-Client-Type` | 可选：`MINIAPP` / `PC_DOCTOR` / `PC_ADMIN` |

### 1.3 实现状态图例

| 标记 | 含义 |
|------|------|
| ✅ | 后端已实现，可关 Mock 联调 |
| ⬜ | 契约已定，后端待实现 |
| STUB | 接口存在，返回占位 / 50301 |
| 🎭 | 仅 PC Mock 实现（`hospital-frontend/src/mock`），关 Mock 不可用 |
| P4/P5 | 分期实现，不阻塞 P1～P3 |

### 1.4 Mock 开关

| 工程 | 配置 | 默认 |
|------|------|------|
| PC `hospital-frontend` | `VITE_USE_MOCK=true` | Mock |
| 小程序 | `config.js` → `USE_MOCK=true` | Mock |

Mock 数据结构 **与本文件一致**。

### 1.5 网关白名单（无需 Token）

- `POST /auth/staff/login`
- `POST /patient/auth/login`
- `POST /patient/auth/wechat`（兼容旧客户端）
- `POST /callback/wechat/pay`
- `GET /auth/health`、各服务 `/health`

### 1.7 医嘱 / 申请状态枚举（v2.1）

**医技申请**（`inspection_request` / `check_request` / `disposal_request`）：

| status | 含义 | 前端 statusLabel |
|--------|------|------------------|
| 10 | 已开立 | 已开立 |
| 20 | 已缴费 | 已缴费 |
| 30 | 执行中 | 执行中 |
| 40 | 已出结果 | 已出结果 |
| 50 | 已退费 | 已退费 |

**处方**（`prescription`）：

| status | 含义 | 前端 statusLabel |
|--------|------|------------------|
| 10 | 已开立 | 已开立 |
| 20 | 已缴费 | 已缴费 |
| 30 | 已发药 | 已发药 |
| 40 | 已退药 | 已退药 |
| 50 | 已退费 | 已退费 |

**医技结果报告结构**（医生站 / LIS / PACS / 处置 共用，见 §5.4、§6）：

| 字段 | 说明 |
|------|------|
| `instrumentData` | 仪器原始数据（只读展示） |
| `aiReportText` | AI 智能报告 |
| `doctorReportText` | 医师意见 |
| `aiReportStatus` | `PENDING` / `READY` / `FAILED` |
| `resultText` | 对外发布合并文本（患者报告摘要可引用） |
| `reportTime` | 报告时间 |

### 1.6 就诊人模型（ADR-016 v1.8）

- JWT **`patientId` = 当前登录的病人账户**（QQ 式切换后 JWT 随之更换）
- `POST /patient/auth/login` 登录；`POST /patient/auth/switch-account` 切换家属账户（须 link 授权）
- Query `visitPatientId` / `patientId`：**兼容字段**；新客户端省略，后端默认 JWT 本人
- 微信 `openid` 仅支付绑定，非登录主体
- 挂号 Body 可选 `memberPatientId` 代家属挂号

---

## 二、接口总览

> 路径均为 §〇 定稿写法。详情见后续章节。

| 状态 | 阶段 | Method | 路径 | 服务 | 权限 |
|------|------|--------|------|------|------|
| ✅ | P1 | POST | `/auth/staff/login` | auth | 公开 |
| ✅ | P1 | GET | `/auth/me` | auth | 已登录 |
| ✅ | P1 | POST | `/auth/token/refresh` | auth | 已登录 |
| ✅ | P1 | POST | `/patient/auth/wechat` | his | 公开（兼容） |
| ✅ | P1 | POST | `/patient/auth/login` | his | 公开 |
| ✅ | P1 | POST | `/patient/auth/switch-account` | his | PATIENT |
| ✅ | P1 | POST | `/patient/auth/wechat/bind` | his | PATIENT |
| ✅ | P1 | GET/PUT | `/patient/profile` | his | PATIENT |
| ✅ | P1 | GET/POST | `/patient/family-members` | his | PATIENT |
| ✅ | P1 | GET | `/patient/departments` | his | PATIENT |
| ✅ | P1 | GET | `/patient/schedules` | his | PATIENT |
| ✅ | P1 | POST | `/patient/registers` | his | PATIENT |
| ✅ | P1 | GET | `/patient/registers` | his | PATIENT |
| ✅ | P1 | GET | `/patient/registers/{registerId}` | his | PATIENT |
| ✅ | P1 | GET | `/patient/registers/{registerId}/queue-status` | his | PATIENT |
| ✅ | P1 | POST | `/patient/registers/{registerId}/cancel` | his | PATIENT |
| ⬜ | P1 | GET | `/patient/registers/{registerId}/orders` | his | PATIENT |
| ✅ | P1 | GET | `/patient/bills` | his | PATIENT |
| ✅ | P1 | POST | `/patient/payments` | his | PATIENT |
| ⬜ | P2 | GET | `/patient/payments` | his | PATIENT |
| ⬜ | P2 | GET | `/patient/payments/{paymentId}` | his | PATIENT |
| ⬜ | P2 | GET | `/patient/refunds` | his | PATIENT |
| P4 | P4 | POST | `/patient/payments/wechat/prepay` | his | PATIENT |
| ✅ | P1 | GET | `/patient/medical-records/{registerId}` | his | PATIENT |
| ✅ | P2 | GET | `/patient/reports` | his | PATIENT |
| ✅ | P2 | GET | `/patient/reports/{type}/{requestId}` | his | PATIENT |
| ✅ | P1 | GET | `/doctor/queues` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | POST | `/doctor/call/{registerId}` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | POST | `/doctor/registers/{registerId}/finish` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | GET/PUT | `/doctor/medical-records/{registerId}` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | POST | `/doctor/medical-records/{registerId}/submit` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | GET | `/doctor/registers/{registerId}/orders` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | GET | `/doctor/diseases` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | GET | `/doctor/medical-technologies` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | GET | `/doctor/drugs` | his | OUTPATIENT_DOCTOR |
| ✅ | P2 | POST | `/doctor/check-requests` | his | OUTPATIENT_DOCTOR |
| ✅ | P2 | POST | `/doctor/inspection-requests` | his | OUTPATIENT_DOCTOR |
| ✅ | P3 | POST | `/doctor/disposal-requests` | his | OUTPATIENT_DOCTOR |
| ✅ | P2+ | GET | `/doctor/check-requests/{id}/result` | his | OUTPATIENT_DOCTOR |
| ✅ | P2+ | GET | `/doctor/inspection-requests/{id}/result` | his | OUTPATIENT_DOCTOR |
| ✅ | P2+ | GET | `/doctor/disposal-requests/{id}/result` | his | OUTPATIENT_DOCTOR |
| ✅ | P3 | POST | `/doctor/prescriptions` | his | OUTPATIENT_DOCTOR |
| STUB | P4 | POST | `/doctor/*/ai-draft` 等 | his | OUTPATIENT_DOCTOR |
| ✅ | P2 | GET | `/lis/queue` | lis | LAB_DOCTOR |
| ✅ | P2 | POST | `/lis/requests/{id}/execute` | lis | LAB_DOCTOR |
| ✅ | P2 | POST | `/lis/requests/{id}/result` | lis | LAB_DOCTOR |
| ⬜ | P2 | GET | `/lis/requests/{id}/result-detail` | lis | LAB_DOCTOR |
| STUB | P4 | POST | `/lis/requests/{id}/ai-report` | lis | LAB_DOCTOR |
| ✅ | P3 | GET | `/pacs/queue` | pacs | CHECK_DOCTOR |
| ✅ | P3 | POST | `/pacs/requests/{id}/execute` | pacs | CHECK_DOCTOR |
| ✅ | P3 | POST | `/pacs/requests/{id}/result` | pacs | CHECK_DOCTOR |
| ⬜ | P3 | GET | `/pacs/requests/{id}/result-detail` | pacs | CHECK_DOCTOR |
| STUB | P4 | POST | `/pacs/requests/{id}/ai-report` | pacs | CHECK_DOCTOR |
| ⬜ | P3 | GET | `/pacs/imaging-studies` | pacs | CHECK_DOCTOR |
| STUB | P4 | POST | `/pacs/imaging/upload` | pacs | CHECK_DOCTOR |
| ✅ | P3 | GET | `/disposal/queue` | disposal | DISPOSAL_DOCTOR |
| ✅ | P3 | POST | `/disposal/requests/{id}/execute` | disposal | DISPOSAL_DOCTOR |
| ✅ | P3 | POST | `/disposal/requests/{id}/result` | disposal | DISPOSAL_DOCTOR |
| ⬜ | P3 | GET | `/disposal/requests/{id}/result-detail` | disposal | DISPOSAL_DOCTOR |
| STUB | P4 | POST | `/disposal/requests/{id}/ai-report` | disposal | DISPOSAL_DOCTOR |
| ✅ | P3 | GET | `/pharmacy/pending` | his | PHARMACIST |
| ✅ | P3 | POST | `/pharmacy/prescriptions/{id}/dispense` | his | PHARMACIST |
| ✅ | P3 | POST | `/pharmacy/prescriptions/{id}/return-drug` | his | PHARMACIST |
| ✅ | P2 | POST | `/registrar/registers` | his | REGISTRAR |
| ✅ | P2 | POST | `/registrar/charges` | his | REGISTRAR |
| ✅ | P2 | GET | `/registrar/departments` | his | REGISTRAR |
| ✅ | P2 | GET | `/registrar/settle-categories` | his | REGISTRAR |
| ✅ | P2 | GET | `/registrar/doctors` | his | REGISTRAR |
| ✅ | P2 | GET | `/registrar/schedules` | his | REGISTRAR |
| ✅ | P2 | POST | `/registrar/refunds` | his | REGISTRAR |
| ✅ | P2 | GET | `/registrar/patients/{medicalRecordNo}/bills` | his | REGISTRAR |
| ✅ | P2 | POST | `/registrar/registers/{registerId}/cancel` | his | REGISTRAR |
| ⬜ | P2 | GET | `/registrar/regist-levels` | his | REGISTRAR |
| ✅ | P1 | GET | `/admin/departments` 等字典 | mgmt | ADMIN |
| ⬜ | P1 | GET/POST/PUT | `/admin/scheduling` | mgmt | ADMIN |
| ⬜ | P1 | GET/CRUD | `/admin/employees` | mgmt | ADMIN |
| ⬜ | P1+ | POST/PUT/DELETE | `/admin/**` 字典写 | mgmt | ADMIN |
| 🎭 | P5 | GET | `/staff/my-schedules` | mgmt 或 his | 已登录职员 |
| 🎭 | P5 | POST | `/staff/schedules/{id}/leave-requests` | 同上 | 已登录职员 |
| 🎭 | P5 | POST | `/staff/leave-requests/{id}/cancel` | 同上 | 已登录职员 |
| 🎭 | P5 | GET | `/admin/leave-requests` | mgmt | ADMIN |
| 🎭 | P5 | POST | `/admin/leave-requests/{id}/approve` | mgmt | ADMIN |
| 🎭 | P5 | POST | `/admin/leave-requests/{id}/reject` | mgmt | ADMIN |
| STUB | P4 | POST | `/ai/triage/chat` | ai-bridge | PATIENT |
| STUB | P4 | POST | `/ai/assistant/stream` | ai-bridge | DOCTOR |
| STUB | P4 | POST | `/ai/diagnosis/suggest` | ai-bridge | DOCTOR |
| STUB | P4 | GET | `/ai/triage/assignments` | ai-bridge | DOCTOR |
| STUB | P4 | POST | `/admin/scheduling/{id}/ai-replace` | mgmt | ADMIN |

---

## 三、hospital-auth · `/auth/**`

### POST `/auth/staff/login` ✅ P1

**Request**：`{ "username": "doctor01", "password": "123456" }`

**Response `data`**：`accessToken`, `refreshToken`, `expiresIn`, `userId`, `employeeId`, `realName`, `roles[]`, `deptId`, `deptName`

**Mock 账号**（密码均为 `123456`）：`doctor01` · `lab01` · `check01` · `pharmacy01` · `registrar01` · `disposal01` · `admin`

### GET `/auth/me` ✅ P1

**Response `data`**：`userId`, `username`, `realName`, `roles[]`

### POST `/auth/token/refresh` ✅ P1

**Request**：`{ "refreshToken": "..." }`

### POST `/internal/token/patient` ✅ P1（仅 his Feign，不经 Gateway）

**Request**：`{ "patientId", "medicalRecordNo" }` → **Response**：`accessToken`, `expiresIn`

---

## 四、患者端 · `/patient/**`（his）

### POST `/patient/auth/login` ✅ P1（主登录入口）

**Request**：`realName`, `idCard`（18 位）, `gender`（1 男 / 2 女）, `birthDate`（`YYYY-MM-DD`）, `phone`（11 位）, `address?`

**Response `data`**：`accessToken`, `expiresIn`, `patientId`, `medicalRecordNo`, `realName`, `isNewPatient`

> JWT **`patientId` = 当前登录的病人账户**（非微信身份）。微信仅用于支付绑定。

### POST `/patient/auth/switch-account` ✅ P1

**Request**：`{ "targetPatientId": 123 }`  
**说明**：QQ 式切换；校验家属 link 双向授权后 **换发目标账户 JWT**。  
**Response `data`**：同 login。

### POST `/patient/auth/wechat/bind` ✅ P1

**Request**：`{ "code" }`（需已登录）  
**说明**：支付前将 openid 绑定到 **当前病人账户**。

### POST `/patient/auth/wechat` ⚠️ 兼容

旧版微信登录入口，新客户端请用 `/login` + `/wechat/bind`。

### GET/PUT `/patient/profile` ✅ P1

**GET `data`**：`id`, `realName`, `medicalRecordNo`, `gender`, `birthDate`, `phone`, `idCard`, `address`

**PUT Request**：`realName`, `gender`, `birthDate`, `phone`, `idCard`, `address`, `settleCategoryId?`

**PUT Response `data`**：同 GET；若身份证合并则额外返回 `identityMerged=true`, `accessToken`, `expiresIn`

### GET/POST `/patient/family-members` ✅ P1

**表**：`patient_family_link` + `patient`（ADR-016）

**GET `data.list[]`**：`memberPatientId`, `realName`, `medicalRecordNo`, `gender`, `birthDate`, `idCard`, `phone`, `address`, `relationType`, `noIdCard`, `guardianName?`, `guardianIdCard?`, `guardianPhone?`, `isSelf`

**POST Request**

| 字段 | 必填 | 说明 |
|------|------|------|
| `realName` | 是 | 就诊人姓名 |
| `gender` | 是 | 1 男 2 女 |
| `birthDate` | 是 | `YYYY-MM-DD` |
| `address` | 否 | 联系住址；挂号写入病历快照 |
| `relationType` | 否 | 1父母 2配偶 3子女 4其他；无身份证患儿默认 3 |
| `idCard` | 有证必填 | 18 位；与 `noIdCard` 互斥 |
| `phone` | 否 | 可空；非空须 11 位且全院唯一 |
| `noIdCard` | 否 | `true` 表示无身份证号患儿 |
| `guardianName` | 无证必填 | 陪诊人姓名（须为账号本人） |
| `guardianIdCard` | 无证必填 | 须与本人档案 `idCard` 一致 |
| `guardianPhone` | 无证必填 | 陪诊人手机 |

**POST Response `data`**：同列表项字段。

### GET `/patient/departments` ✅ P1

**Query**：`deptType=1`（门诊）  
**Response `data.list[]`**：`id`, `deptCode`, `deptName`, `deptType`

### GET `/patient/schedules` ✅ P1

**Query**：`deptId`, `employeeId?`, `registLevelId?`, `workDate?`  
**Response `data.list[]`**：`schedulingId`, `deptName`, `doctorName`, `levelName`, `workDate`, `noonLabel`, `registFee`, `remainQuota`

### POST `/patient/registers` ✅ P1

**Request**：`{ "schedulingId", "memberPatientId?" }`  
**Response `data`**：`registerId`, `billId`, `amount`, `visitState`（0 待支付）

### GET `/patient/registers` ✅ P1

**Query**：`patientId`（= visitPatientId）, `visitState?`, `page`, `pageSize`  
**Response `data.list[]`**：`registerId`, `patientName`, `deptName`, `doctorName`, `registLevelName`, `visitState`, `workDate`, `noonLabel`, `registFee`

### GET `/patient/registers/{registerId}` ✅ P1

### GET `/patient/registers/{registerId}/queue-status` ✅ P1

**Response `data`**：`queueNo`, `aheadCount`, `visitState`, `queueHint?`

### POST `/patient/registers/{registerId}/cancel` ✅ P1

**Request**：`{ "reason": "..." }`

### GET `/patient/registers/{registerId}/orders` ⬜ P1

**页面**：小程序「医嘱进度」、PC 医生站医嘱面板（患者端同结构）

**Response `data`**

```json
{
  "registerId": 3001,
  "list": [
    {
      "kind": "inspection",
      "typeLabel": "检验",
      "requestId": 61001,
      "itemName": "血常规",
      "status": 40,
      "statusLabel": "已出结果"
    },
    {
      "kind": "disposal",
      "typeLabel": "处置记录",
      "requestId": 63001,
      "itemName": "洗胃",
      "status": 40,
      "statusLabel": "已出结果"
    }
  ],
  "checks": [],
  "inspections": [],
  "disposals": [],
  "prescriptions": []
}
```

| kind | 表 | status | statusLabel 见 §1.7 |
|------|-----|--------|----------------------|
| inspection | `inspection_request` | 10/20/30/40/50 | 医技 |
| check | `check_request` | 同上 | 医技 |
| disposal | `disposal_request` | 同上 | 医技 |
| prescription | `prescription` | 10/20/30/40/50 | **处方**（30=已发药） |

### GET `/patient/bills` ✅ P1

**Query**：`patientId`, `status?`（0 待支付）, `registerId?`, `scope?`（outpatient/exam）  
**Response `data.list[]`**：`id`, `billTitle`, `bizType`, `amount`, `status`, `registerId`

### POST `/patient/payments` ✅ P1（Mock 批量支付）

**Request**：`{ "billIds": [81001, 81002] }`  
**Response `data`**：`paymentId`, `paidAmount`, `status`

### GET `/patient/payments` ⬜ P2

**Query**：`page`, `pageSize`, `registerId`, `patientId`

### GET `/patient/payments/{paymentId}` ⬜ P2

### GET `/patient/refunds` ⬜ P2

### POST `/patient/payments/wechat/prepay` P4

真微信支付预下单；首期演示用 Mock `POST /patient/payments`。

### GET `/patient/medical-records/{registerId}` ✅ P1

**仅 `medical_record.status=2` 对患者可见**  
**Response `data`**：`readme`, `present`, `diagnosis`, `cure`, …

### GET `/patient/reports` ✅ P2

**Query**：`type`（all/lab/exam/disposal）, `patientId`  
**Response `data.list[]`**：`requestId`, `type`, `typeLabel`（disposal→**处置记录**）, `reportName`, `reportTime`, `summary`

### GET `/patient/reports/{type}/{requestId}` ✅ P2

**type**：`lab` | `exam` | `disposal`  
**Response `data`**：`reportName`, `typeLabel`, `purpose`, `bodyPart`, `resultText`, `reportTime`, `status`

---

## 五、门诊医生 · `/doctor/**`（his）

> PC 医生工作台（`WorkspaceView`）已联调 Mock；**无批量开单接口**，前端对多选项目 **逐条 POST** `/*-requests`。

### GET `/doctor/queues` ✅ P1（部分字段 ⬜）

**Query**：`visitState?`（1 已挂号 / 2 接诊中；省略时默认今日 `visit_state IN (1,2)`）, `keyword?`, `page`, `pageSize`

**Response `data.list[]`**

| 字段 | 状态 | 说明 |
|------|------|------|
| `registerId` | ✅ | |
| `medicalRecordNo` | ✅ | |
| `patientName` | ✅ | |
| `gender` | ✅ | 1 男 / 2 女 |
| `age` | ✅ | 由 `birth_date` 推算 |
| `visitState` | ✅ | 0 待支付 / 1 已挂号 / 2 接诊中 / 3 看诊结束 |
| `registLevelName` | ✅ | |
| `registTime` | ✅ | |
| `noonLabel` | ⬜ | 上午/下午；来自 `register.noon_type` |
| `triageLevel` | ⬜ STUB | `EMERGENCY` / `URGENT` / `NORMAL`；见 §10.1 |
| `triageNote` | ⬜ STUB | AI 分诊说明 |

**分诊数据来源（二选一，推荐 A）**

- **A（推荐）**：队列项直接带 `triageLevel` / `triageNote`（Mock 现状）
- **B**：`GET /ai/triage/assignments?registerIds=3001,3002` 返回 map，前端合并

### POST `/doctor/call/{registerId}` ✅ P1

**前置**：`visit_state = 1`（已挂号）  
**效果**：`visit_state` → 2（接诊中）  
**Response `data`**：`registerId`, `visitState`

### POST `/doctor/registers/{registerId}/finish` ✅ P1

**页面**：医生工作台「结束看诊」  
**前置**：当前医生、`visit_state = 2`  
**效果**：`visit_state` → 3（看诊结束）  
**Response `data`**：`registerId`, `visitState`

### GET/PUT `/doctor/medical-records/{registerId}` ✅ P1

**GET/PUT 字段**：`readme`, `present`, `presentTreat`, `history`, `allergy`, `physique`, `diagnosis`, `cure`, `checkAdvice`, `inspectionAdvice`

**疾病关联（✅ 已贯通）**

| 字段 | 用途 | 前端 | 后端 |
|------|------|------|------|
| `diseaseIds` | 扁平 ID 列表（兼容） | 保存/提交时发送 | ✅ 已持久化 |
| `diseaseEntries` | 结构化：`[{ diseaseId, diseaseType }]`，`diseaseType` 1=主要 / 2=次要 | 保存/提交时发送 | ✅ 已写 `medical_record_disease` |

> 前端 `AiDiagnosisBar` 将 AI 诊断写入 `diagnosis` 后一并 PUT 保存。  
> **GET/PUT/submit Response** 另含 `status`（0 书写中 / 1 已保存 / 2 已确诊提交）、`statusLabel`。

### POST `/doctor/medical-records/{registerId}/submit` ✅ P1

**页面**：医生工作台「确诊提交」按钮（`confirmMedicalRecord` → 本路径，**非** `/medical-record/confirm`）  
**Request**：同 PUT 病历字段，可含 `diseaseEntries`  
**效果**：持久化 Request 体 → `medical_record.status` → 2，患者端 `GET /patient/medical-records/{id}` 可见  
**Response `data`**：同 GET 病历，含 `status`, `statusLabel`, `diseaseEntries`, `diseaseIds`

### GET `/doctor/diseases` ✅ P1

**页面**：医生工作台病历「ICD 疾病」多选  
**Query**：`keyword?`, `page`, `pageSize`（默认 50）  
**Response `data.list[]`**：`id`, `diseaseCode`, `diseaseName`, `diseaseCategory?`  
**说明**：只读查共享库 `disease` 表；与 `/admin/diseases` 字段一致，供门诊医生使用（无需 ADMIN 角色）。

### GET `/doctor/medical-technologies` ✅ P1

**页面**：医生工作台「开检查 / 开检验 / 开处置」弹窗（`DoctorTechOrderDialog`）  
**Query**：`keyword?`, `techType?`（CHECK / INSPECTION / DISPOSAL）, `page`, `pageSize`（默认 50）  
**Response `data.list[]`**：`id`, `itemCode`, `itemName`, `techType`, `price`, `deptId?`  
**说明**：只读查共享库 `medical_technology` 表；与 `/admin/medical-technologies` 字段一致，供门诊医生开立医技医嘱（无需 ADMIN 角色）。

### GET `/doctor/drugs` ✅ P1

**页面**：医生工作台「手工开处方 / AI 处方草稿」弹窗（`DoctorPrescriptionDialog`、`AiPrescriptionDraftDialog`）  
**Query**：`keyword?`, `page`, `pageSize`（默认 50）  
**Response `data.list[]`**：`id`, `drugCode`, `drugName`, `drugFormat`, `drugDosage`, `drugType`, `unit`, `retailPrice`, `stockQty`  
**说明**：只读查共享库 `drug_info` 表；与 `/admin/drugs` 字段一致，供门诊医生开立处方（无需 ADMIN 角色）。

### GET `/doctor/registers/{registerId}/orders` ✅ P1

**页面**：医生工作台「本次就诊医嘱」面板（`RegisterOrdersPanel`）  
**Response**：与 §4 `GET /patient/registers/{id}/orders` **完全相同**（含 `list[]` 汇总 + `checks/inspections/disposals/prescriptions` 明细）

明细数组中已含 `resultText`、`resultTime`（status ≥ 40 时有值），供前端展示医技结果。

### 5.5 医嘱结果展示（无独立聚合接口）

> **不存在** `GET /doctor/registers/{registerId}/results`。历史 Mock/旧文档曾使用该路径，**禁止**新增后端实现。

**定稿做法（PC 前端 `fetchRegisterResults` / `buildRegisterResultsFromOrders`）**：

1. 调用 `GET /doctor/registers/{registerId}/orders` 一次；
2. 遍历 `data.list`，对 `kind ∈ { inspection, check, disposal }` 且 `status >= 40` 的项，从对应明细数组（`inspections` / `checks` / `disposals`）取 `resultText`、`resultTime`；
3. 组装为 UI 用的 `{ kind, requestId, typeLabel, itemName, resultText, reportTime }[]`。

**逐条查结果**（可选）：`GET /doctor/check-requests/{id}/result` 等（§5.4），适用于详情页或 orders 明细缺字段时。

### POST `/doctor/check-requests` ✅ P2

**Request**：`{ "registerId", "medicalTechnologyId", "purpose?", "bodyPart?", "remark?" }`  
**Response `data`**：`checkRequestId`, `itemName`, `status`（10）, `billId`, `amount?`, `message?`

### POST `/doctor/inspection-requests` ✅ P2

同上，返回 `inspectionRequestId`

### POST `/doctor/disposal-requests` ✅ P3

同上，返回 `disposalRequestId`

### GET `/doctor/check-requests/{id}/result` ✅（响应扩展 ⬜）

### GET `/doctor/inspection-requests/{id}/result` ✅（响应扩展 ⬜）

### GET `/doctor/disposal-requests/{id}/result` ✅（响应扩展 ⬜）

**前置**：`status >= 40`  
**Response `data`**（v2.1，对齐 `ResultReportSections`）：

```json
{
  "checkRequestId": 62001,
  "itemName": "头部 CT",
  "instrumentData": "…仪器原始数据…",
  "aiReportText": "…AI 报告…",
  "doctorReportText": "…医师意见…",
  "aiReportStatus": "READY",
  "resultText": "…对外摘要…",
  "reportTime": "2026-06-09T10:00:00+08:00"
}
```

> 当前后端仅返回 `resultText`；需扩展至完整结构（§1.7）。

### POST `/doctor/prescriptions` ✅ P3

**Request**

```json
{
  "registerId": 30002,
  "remark": "门诊处方",
  "items": [{
    "drugId": 1,
    "quantity": 2,
    "usageMethod": "口服",
    "dosage": "0.5g",
    "frequency": "tid",
    "days": 7,
    "entrust": "饭后服用"
  }]
}
```

**Response `data`**：`prescriptionId`, `totalAmount`, `status`（10）, `message?`

### 5.1 AI 辅助诊疗（P4 · ADR-015）STUB/⬜

| Method | 路径 | 说明 |
|--------|------|------|
| POST | `/ai/diagnosis/suggest` | 智能诊断建议（ai-bridge） |
| POST | `/doctor/check-requests/ai-draft` | 生成检查草稿 |
| PUT | `/doctor/check-requests/ai-draft/{draftId}` | 编辑草稿 |
| POST | `/doctor/check-requests/ai-draft/{draftId}/confirm` | 确认提交 → 正式开单 |
| POST | `/doctor/inspection-requests/ai-draft` | 检验（对称） |
| PUT | `/doctor/inspection-requests/ai-draft/{draftId}` | |
| POST | `/doctor/inspection-requests/ai-draft/{draftId}/confirm` | |
| POST | `/doctor/disposal-requests/ai-draft` | 处置（对称） |
| PUT | `/doctor/disposal-requests/ai-draft/{draftId}` | |
| POST | `/doctor/disposal-requests/ai-draft/{draftId}/confirm` | |
| POST | `/doctor/prescriptions/ai-draft` | 处方草稿 P4 |
| PUT | `/doctor/prescriptions/ai-draft/{draftId}` | |
| POST | `/doctor/prescriptions/ai-draft/{draftId}/confirm` | |

**`POST /ai/diagnosis/suggest` Request**（`AiDiagnosisBar`）：

```json
{
  "registerId": 3001,
  "readme": "头痛 3 天",
  "present": "…",
  "presentTreat": "…",
  "history": "…",
  "allergy": "无",
  "physique": "…",
  "diagnosis": "…",
  "cure": "…",
  "checkAdvice": "…",
  "inspectionAdvice": "…"
}
```

**Response `data`**：`stub`, `suggestions[]`, `needCheck`, `needInspection`, `needDisposal`, `reason`

**`POST /doctor/*-requests/ai-draft` Response `data`**（生成草稿，**不直接开单**）：

```json
{
  "stub": true,
  "draftId": 8001,
  "draftType": "CHECK",
  "registerId": 3001,
  "aiReason": "根据主诉建议完善影像检查",
  "items": [{
    "medicalTechnologyId": 1,
    "itemName": "头部 CT",
    "purpose": "排除颅内病变",
    "bodyPart": "头部",
    "remark": ""
  }]
}
```

**流程**：填写病历 → `diagnosis/suggest` → `ai-draft` → 医生在开单弹窗勾选确认 → 逐条 POST 正式开单；**禁止** AI 未经确认直接开立。

---

## 六、医技执行

> 医技队列 UI（`TechQueuePanel`）**目标**使用 **result-detail** 加载三段式报告，**ai-report** 生成 AI 报告，**result** 保存医师确认后的结果。  
> **联调现状**：`TechQueuePanel` 简化版仅编辑 `resultText`（+ 可选 `resultAttachment`）；`ResultReportSections.vue` 已存在但未接入队列页。`result-detail` / `ai-report` 后端 ⬜。

### 6.0 前端 Method 对齐

| 定稿 | 当前 `hospital-frontend` | 文件 |
|------|--------------------------|------|
| `POST /{svc}/requests/{id}/result` | ❌ `PUT` | `lis.js`, `pacs.js`, `disposal.js` |

后端 LIS/PACS/Disposal Controller 均为 `@PostMapping`；前端 PUT 将返回 **405**，见附录 G。

### 6.1 医技结果 API 对称约定（LIS / PACS / Disposal）

| Method | 路径模板 | 说明 |
|--------|----------|------|
| GET | `/{svc}/requests/{id}/result-detail` | 加载报告详情（§1.7 全字段） |
| POST | `/{svc}/requests/{id}/ai-report` | 触发 AI 报告生成（STUB → `aiReportStatus=READY`） |
| POST | `/{svc}/requests/{id}/result` | 保存结果并 `status→40` |

**`POST .../result` Request**（v2.1，推荐对象；兼容旧版纯字符串 `resultText`）：

```json
{
  "aiReportText": "AI 检验报告正文…",
  "doctorReportText": "医师意见…"
}
```

服务端应合成 `resultText`（如 `AI：…\n医师：…`）供患者端 `GET /patient/reports` 摘要。

**`POST .../ai-report` Response `data`**：同 result-detail 结构，`aiReportText` 已填充。

### LIS · `/lis/**` · hospital-lis :9103

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/lis/health` | 健康检查 |
| ✅ | GET | `/lis/queue?status=20` | 待执行队列 |
| ✅ | POST | `/lis/requests/{id}/execute` | status→30（执行中） |
| ✅ | POST | `/lis/requests/{id}/result` | 保存结果 → status 40 |
| ⬜ | GET | `/lis/requests/{id}/result-detail` | 报告详情 |
| STUB | POST | `/lis/requests/{id}/ai-report` | AI 检验报告 |

**队列项**：`inspectionRequestId`, `medicalRecordNo`, `patientName`, `itemName`, `itemPrice`, `status`, `triageLevel?`, `triageNote?`

### PACS · `/pacs/**` · hospital-pacs :9104

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/pacs/health` | |
| ✅ | GET | `/pacs/queue?status=20` | `checkRequestId` |
| ✅ | POST | `/pacs/requests/{id}/execute` | status→30 |
| ✅ | POST | `/pacs/requests/{id}/result` | 保存结果 |
| ⬜ | GET | `/pacs/requests/{id}/result-detail` | 报告详情 |
| STUB | POST | `/pacs/requests/{id}/ai-report` | AI 影像分析（可联动 hospital-ai CNN） |
| ⬜ | GET | `/pacs/imaging-studies` | 影像任务列表；Query：`status`、`patientId`、`medicalRecordNo`、`page`、`pageSize` |
| STUB | POST | `/pacs/imaging/upload` | 影像上传 |

**imaging-studies 列表项**：`studyId`, `checkRequestId`, `patientId`, `patientName`, `medicalRecordNo`, `itemName`, `modality`, `status`, `uploadStatus`, `resultReady`

> 跨模块按患者查影像、MinIO 路径与 `patient_id` 对应关系：**[IMAGING_DATA_ACCESS.md](./IMAGING_DATA_ACCESS.md)**

### Disposal · `/disposal/**` · hospital-disposal :9105

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/disposal/health` | |
| ✅ | GET | `/disposal/queue?status=20` | `disposalRequestId` |
| ✅ | POST | `/disposal/requests/{id}/execute` | status→30 |
| ✅ | POST | `/disposal/requests/{id}/result` | 保存结果 |
| ⬜ | GET | `/disposal/requests/{id}/result-detail` | 报告详情 |
| STUB | POST | `/disposal/requests/{id}/ai-report` | AI 处置记录辅助 |

---

## 七、药房 · `/pharmacy/**`（his）

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/pharmacy/pending?status=20` | 待发药处方 |
| ✅ | POST | `/pharmacy/prescriptions/{id}/dispense` | status→30 |
| ✅ | POST | `/pharmacy/prescriptions/{id}/return-drug` | 退药 → 窗口退费 |

---

## 八、收费员 · `/registrar/**`（his）

### 8.1 字典与号别

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/registrar/departments` | 门诊科室 |
| ✅ | GET | `/registrar/settle-categories` | 结算类别 |
| ⬜ | GET | `/registrar/regist-levels` | 挂号号别（普通/专家）；**定稿**，替代收费员调 `/admin/**` |
| ✅ | GET | `/registrar/doctors` | Query `deptId` |
| ✅ | GET | `/registrar/schedules` | Query `deptId`, `employeeId`, `registLevelId`, `workDate` |

> **前端现状（待改）**：`registrar.js` → `fetchRegistLevels()` 暂请求 `GET /admin/regist-levels`（REGISTRAR 角色可能 403）。联调前应实现 `/registrar/regist-levels` 只读代理，或前端在 Mock 关闭时隐藏号别选择。

### 8.2 窗口业务

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | POST | `/registrar/registers` | 窗口挂号（建档+占号+待支付 bills，`visit_state=0`） |
| ✅ | POST | `/registrar/charges` | 窗口收费（批量待缴，`payChannel` 记账） |
| ✅ | POST | `/registrar/refunds` | `{ "billId", "reason" }` |
| ✅ | GET | `/registrar/patients/{medicalRecordNo}/bills` | 按病历号查账 |
| ✅ | POST | `/registrar/registers/{registerId}/cancel` | 窗口退号 |

**GET `/registrar/schedules` Query**：`deptId`, `employeeId`, `registLevelId`, `workDate`（ISO 日期，缺省为当天）

**Response `list[]` 字段**：`schedulingId`, `deptId`, `deptName`, `employeeId`, `employeeName`, `employeeTitle`, `registLevelId`, `registLevelName`, `registFee`, `workDate`, `noonType`, `noonLabel`, `timeRange`, `totalQuota`, `usedQuota`, `remainQuota`（含号源已满记录，前端可置灰）

**GET `/registrar/doctors` Response `list[]`**：`employeeId`, `realName`, `title`, `clinicRole`（`REGULAR`/`EXPERT`）, `expertSessionCount`（专家近 7 天专家号半天数，普通医生无此字段）

**窗口挂号 Request**：

```json
{
  "patientName": "张三",
  "gender": 1,
  "phone": "13800138000",
  "idCard": "110101199001011234",
  "settleCategoryId": 1,
  "needRecordBook": true,
  "schedulingId": 1,
  "deptId": 1,
  "employeeId": 1,
  "registLevelId": 1
}
```

**窗口挂号 Response `data`**：`registerId`, `patientId`, `medicalRecordNo`, `billIds[]`, `amount`, `visitState`（固定 `0` 待支付）, `deptName`, `doctorName`, `workDate`, `noonLabel`, `registLevelName`, `message`

> `register.channel = WINDOW`；`registrar_id` 为当前收费员 `employeeId`；支付在收费页完成。

**窗口收费 Request**：

```json
{
  "billIds": [81001, 81002],
  "payChannel": "CASH"
}
```

| `payChannel` | 说明 |
|--------------|------|
| `CASH` / `WECHAT` / `ALIPAY` / `INSURANCE` / `SCAN` | 开发期均记账，不调第三方支付 SDK |

**窗口收费 Response `data`**：`paymentId`, `paidAmount`, `message`

> `REGISTER` 账单付清后 `visit_state` → `1`（已挂号）；`MEDICAL_BOOK` 付清后更新 `patient.need_medical_book`。

> **排班/字典查询**：窗口挂号页使用 **`GET /registrar/departments`**、**`/registrar/settle-categories`**、**`/registrar/regist-levels`**（⬜）、**`/registrar/doctors`**、**`/registrar/schedules`**（REGISTRAR 角色）；**禁止**调用 `/admin/**`（除临时联调号别只读，见 §8.1）。

### 8.5 职员自助 · `/staff/**` 🎭 P5

> **仅 Mock**（`hospital-frontend/src/mock/scheduling-leave.js`）。关 `VITE_USE_MOCK` 后不可用，后端 ⬜ 未实现。  
> 页面：各角色「我的排班」（`MyScheduleView.vue`）。

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| 🎭 | GET | `/staff/my-schedules` | Query：`employeeId`, `workDateFrom?`；返回本人未来排班 |
| 🎭 | POST | `/staff/schedules/{schedulingId}/leave-requests` | Body：`{ employeeId, reason }` |
| 🎭 | POST | `/staff/leave-requests/{leaveRequestId}/cancel` | Body：`{ employeeId }` |

---

## 九、管理端 · `/admin/**` · hospital-management :9107

### 9.1 字典只读 ✅ P1

| Method | 路径 | 表 |
|--------|------|-----|
| GET | `/admin/health` | — |
| GET | `/admin/departments` | `department` |
| GET | `/admin/departments/{id}` | |
| ✅ POST | `/admin/departments` | 创建科室 |
| ✅ PUT | `/admin/departments/{id}` | 更新科室（不可改 `deptCode`） |
| ✅ DELETE | `/admin/departments/{id}` | 逻辑删 |
| GET | `/admin/regist-levels` | `regist_level` |
| GET | `/admin/settle-categories` | `settle_category` |
| GET | `/admin/medical-technologies` | `medical_technology` |
| GET | `/admin/drugs` | `drug_info` |
| GET | `/admin/diseases` | `disease` |

**Query 公共**：`page`, `pageSize`, `keyword`  
**医技 `list[]`**：`id`, `itemCode`, `itemName`, `techType`（CHECK/INSPECTION/DISPOSAL）, `price`

### 9.2 排班 ✅ P1

| Method | 路径 | 说明 |
|--------|------|------|
| ✅ GET | `/admin/scheduling` | 排班列表 |
| ✅ POST | `/admin/scheduling` | 创建 |
| ✅ PUT | `/admin/scheduling/{id}` | 更新（含替班：改 `employeeId`） |
| ✅ POST | `/admin/scheduling/{id}/publish` | 发布 |
| P5 | POST | `/admin/scheduling/ai-suggest` | AI 排班建议 |
| STUB | P5 | POST | `/admin/scheduling/{id}/ai-replace` | 应用 AI 推荐（**定稿**有独立接口；Mock 用 PUT 更新代替） |
| P5 | POST | `/admin/scheduling/solve` | Timefold 求解 |

> **前端**：`admin.js` 已改为 `/admin/scheduling/**`；Mock 与 `SchedulingView.vue` 功能完整；关 Mock 可联调科室/员工/排班 CRUD。

**Query**：`workDate`, `deptId`, `employeeId`  
**Request 示例**：`{ "deptId", "employeeId", "registLevelId", "workDate", "noonType", "totalQuota" }`  
> `scheduling` 表不存 `dept_id`；出诊科室经 `employee.dept_id` 推导（DATABASE v1.14）。

### 9.5 排班请假审批 · `/admin/leave-requests/**` 🎭 P5

> **仅 Mock**。与 §8.5 配对；页面：`SchedulingView.vue` 请假 Tab。  
> **定稿**：替班通过 `PUT /admin/scheduling/{id}` 更新排班，**无**单独 `ai-replace` 时 Mock 亦走 PUT。

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| 🎭 | GET | `/admin/leave-requests` | Query：`status?` |
| 🎭 | POST | `/admin/leave-requests/{id}/approve` | Body：`{ adminName? }` |
| 🎭 | POST | `/admin/leave-requests/{id}/reject` | Body：`{ remark, adminName? }` |

### 9.3 员工 ✅ P1

| Method | 路径 | 说明 |
|--------|------|------|
| ✅ GET | `/admin/employees` | Query: `deptId`, `roleType` |
| ✅ CRUD | `/admin/employees/{id}` | 管理端维护 |

### 9.4 字典 CRUD 写操作 ⬜ P2

各资源 `POST` / `PUT` / `DELETE`（逻辑删）与只读路径对称，见 §9.1 资源名。

---

## 十、hospital-ai-bridge · `/ai/**` :9106

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| STUB | GET | `/ai/health` | |
| STUB | POST | `/ai/triage/chat` | 患者问诊 SSE |
| STUB | GET | `/ai/triage/assignments` | 挂号分诊等级（见 §5 队列 `triageLevel`） |
| STUB | POST | `/ai/assistant/stream` | 医生助理 SSE |
| STUB | POST | `/ai/diagnosis/suggest` | ADR-015 智能诊断（Request 见 §5.1） |
| P4 | POST | `/ai/prescription/draft` | 处方草稿 |
| P5 | POST | `/ai/scheduling/suggest` | 排班 NL 建议 |

### GET `/ai/triage/assignments` STUB P4

**Query**：`registerIds`（逗号分隔）  
**Response `data.list[]`**：`registerId`, `triageLevel`（`EMERGENCY|URGENT|NORMAL`）, `triageNote`

> 若采用 §5 方案 A，本接口可省略，分诊字段由 his 队列直接返回。

**diagnosis/suggest Response `data`**：`stub`, `suggestions[]`, `needCheck`, `needInspection`, `needDisposal`, `reason`

---

## 十一、hospital-ai（Python CNN · 内网）

**Base URL**：`http://hospital-ai:8000`（不经 Gateway）

| 阶段 | Method | 路径 | 说明 |
|------|--------|------|------|
| P4 | GET | `/v1/health` | |
| P4 | POST | `/v1/inference/jobs` | 异步推理 |
| P4 | GET | `/v1/inference/jobs/{jobId}` | 轮询状态 |

pacs 内网回调：`POST http://hospital-pacs:9104/internal/imaging/callback`

---

## 十二、Webhook 与回调

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ⬜ | POST | `/callback/wechat/pay` | 微信支付通知 |
| ⬜ | POST | `/callback/wechat/refund` | 微信退款通知 |

---

## 附录 A · 页面 ↔ 接口速查

### 患者微信小程序

| 页面 | 主要接口 |
|------|----------|
| 登录 | `POST /patient/auth/login`（完整档案）；`POST /patient/auth/switch-account` 切换账户 |
| 添加账户 | `POST /patient/auth/login?add=1` 或登录页添加模式 |
| 按科室挂号 | `GET /patient/departments`, `/schedules`, `POST /registers` |
| 按疾病导诊 | 本地规则 → 跳转挂号页 |
| 待缴/已缴 | `GET /patient/bills`, `POST /payments`, `GET /payments` |
| 挂号记录/排队/退号 | `GET /registers`, `/registers/{id}`, `/queue-status`, `POST .../cancel` |
| 就诊人 | `GET/POST /patient/family-members` |
| 电子病历 | `GET /patient/medical-records/{registerId}` |
| 报告 Tab | `GET /patient/reports`, `/reports/{type}/{id}` |
| 医嘱进度 | `GET /patient/registers/{id}/orders` |
| AI 问诊 P4 | `POST /ai/triage/chat` |

### PC 端（`hospital-frontend`）

| 角色 | 页面 | 主要接口（定稿） | Mock |
|------|------|------------------|------|
| 医生 | 工作台 | `/doctor/queues`, `/call/{id}`, `/medical-records/*`, `/registers/{id}/orders`（§5.5 组装结果）, `/registers/{id}/finish` ✅, `/medical-records/{id}/submit` ✅, `/*-requests` 开单 | 部分 |
| 医生 | 我的排班 | §8.5 `/staff/**` | 🎭 |
| 医生 | AI 辅助 | `/ai/diagnosis/suggest` STUB, `/*/ai-draft` STUB | 🎭 |
| LIS/PACS/处置 | 队列 | `/lis/**`, `/pacs/**`, `/disposal/**`；result 用 **POST** | 部分 |
| PACS | 影像任务 | `GET /pacs/imaging-studies` ⬜ | 🎭 |
| 药师 | 发药 | `/pharmacy/pending`, `.../dispense`, `.../return-drug` | ✅ |
| 收费员 | 挂号/收费/退费 | §8 `/registrar/**` | ✅ |
| 管理员 | 员工/科室 | `/admin/employees` CRUD ⬜, `/admin/departments` GET ✅ | 部分 |
| 管理员 | 排班/请假 | §9.2 `/admin/scheduling/**` ⬜, §9.5 leave 🎭 | 🎭 |

> 完整「api 文件 → 路径」见 **附录 F**。

---

## 附录 B · 数据表写归属

| 表 | 开立/创建 | 状态变更 | 结果录入 |
|----|-----------|----------|----------|
| `register` | patient/registrar POST | doctor call/finish | — |
| `medical_record` | doctor PUT | doctor submit | — |
| `inspection_request` | doctor POST | 缴费/refund; lis 执行/结果 | lis |
| `check_request` | doctor POST | 缴费; pacs 执行/结果 | pacs |
| `disposal_request` | doctor POST | 缴费; disposal 执行/结果 | disposal |
| `prescription` | doctor POST | 缴费; pharmacy 发药 | — |
| `bill` / `payment_record` | 开单/挂号 | patient/registrar 支付 | — |

---

## 附录 C · 错误码（业务）

| code | 说明 |
|------|------|
| 40001 | 号源已满 |
| 40002 | 状态不允许（如未缴费执行） |
| 40003 | 就诊人未绑定 |
| 50301 | AI 模块未启用 |
| 50302 | CNN 未启用 |

---

## 附录 E · 后端待办（v2.2，按优先级）

| 优先级 | 模块 | 接口 / 改动 |
|--------|------|-------------|
| P0 | his · 挂号 | `GET /registrar/regist-levels`（REGISTRAR 只读） |
| P0 | 前端 | 医技 result：`PUT`→`POST`；admin：`schedules`→`scheduling`（附录 G） |
| P0 | his · 医生/患者 | 患者端 `GET /patient/registers/{id}/orders` ⬜ |
| P0 | lis/pacs/disposal | `GET .../result-detail`, `POST .../ai-report` |
| P1 | his · 医生 | 队列补 `noonLabel`；`triageLevel/triageNote` |
| P1 | his · 医生 | `GET /doctor/*-requests/{id}/result` 扩展 §1.7 字段 |
| P1 | lis/pacs/disposal | `POST .../result` 支持 `{ aiReportText, doctorReportText }` |
| P2 | ai-bridge | STUB：`diagnosis/suggest`、`*-requests/ai-draft` |
| P2 | mgmt | `/admin/scheduling/**` CRUD、`/admin/employees` CRUD |
| P5 | mgmt/his | §8.5 `/staff/**`、§9.5 `/admin/leave-requests/**`（或合并进 scheduling 域） |

---

## 附录 F · PC 前端 `src/api` ↔ 定稿路径

| 文件 | 函数 | 定稿路径 | 前端当前 | 后端 |
|------|------|----------|----------|------|
| `doctor.js` | `fetchDoctorQueue` | `GET /doctor/queues` | ✅ | ✅ |
| `doctor.js` | `callPatient` | `POST /doctor/call/{id}` | ✅ | ✅ |
| `doctor.js` | `finishVisit` | `POST /doctor/registers/{id}/finish` | ✅ | ✅ |
| `doctor.js` | `fetchMedicalRecord` | `GET /doctor/medical-records/{id}` | ✅ | ✅ |
| `doctor.js` | `saveMedicalRecord` | `PUT /doctor/medical-records/{id}` | ✅ | ✅ |
| `doctor.js` | `confirmMedicalRecord` | `POST /doctor/medical-records/{id}/submit` | ✅ | ✅ |
| `doctor.js` | `fetchRegisterOrders` | `GET /doctor/registers/{id}/orders` | ✅ | ✅ |
| `doctor.js` | `fetchRegisterResults` | §5.5（无 HTTP 聚合） | ✅ 客户端组装 | — |
| `doctor.js` | `fetchMedicalTechnologies` | `GET /doctor/medical-technologies` | ✅ | ✅ |
| `doctor.js` | `fetchDrugs` | `GET /doctor/drugs` | ✅ | ✅ |
| `registrar.js` | `fetchRegistLevels` | `GET /registrar/regist-levels` | ❌ `/admin/regist-levels` | ⬜ |
| `lis.js` | `saveLisResult` | `POST /lis/requests/{id}/result` | ❌ PUT | ✅ POST |
| `pacs.js` | `savePacsResult` | `POST /pacs/requests/{id}/result` | ❌ PUT | ✅ POST |
| `disposal.js` | `saveDisposalResult` | `POST /disposal/requests/{id}/result` | ❌ PUT | ✅ POST |
| `admin.js` | `fetchAdminSchedules` 等 | `/admin/scheduling/**` | ✅ `/admin/scheduling/**` | ✅ |
| `scheduling.js` | 全部 | §8.5、§9.5 | 🎭 Mock 路径一致 | ⬜ |

---

## 附录 G · 前端整改清单（对齐 v2.2）

| # | 项 | 定稿 | 当前 | 优先级 |
|---|-----|------|------|--------|
| G1 | 医技保存结果 | `POST .../result` | lis/pacs/disposal 已 POST ✅ | — |
| G2 | 管理端排班 | `/admin/scheduling/**` | ✅ `/admin/scheduling/**` | — |
| G3 | 挂号号别 | `GET /registrar/regist-levels` | `GET /admin/regist-levels` | P0 |
| G4 | 医嘱 status 30 文案 | 「执行中」 | 「执行完成」 | P1 |
| G5 | 医技 UI | `ResultReportSections` + result-detail | 单字段 resultText | P1 |
| G6 | 病历疾病 | 已联调 `diseaseEntries` | ✅ | — |
| G8 | 医生开单字典 | `GET /doctor/medical-technologies`、`/doctor/drugs` | ✅ | P0 |
| G7 | 废弃文档 | 仅维护 `docs/API.md` | `API_CONTRACT.md` 历史路径 | P0 |

---

## 附录 D · 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.3 | 2026-06-10 | 医生工作台字典只读：`GET /doctor/medical-technologies`、`GET /doctor/drugs`；附录 G8 |
| v2.2 | 2026-06-10 | 对齐 PC 前端审计：§0.3～0.4 前端现状、§5.5 无聚合 results、§8.1 regist-levels、§8.5/§9.5 Mock 排班请假、§6.0 Method 对齐、附录 F/G |
| v2.1 | 2026-06-09 | 对齐 PC 医生工作台/医技队列 Mock：finish/orders、三段式报告、result-detail/ai-report、状态枚举、AI 草稿结构、白名单与附录 A |
| v2.0 | 2026-06-04 | **合并**原 `API.md` + `API_INTERFACE_SPEC.md` 为唯一契约；**统一路径**（§〇）；增加实现状态总览 |
| v1.4 | 2026-06 | DATABASE v1.14 对齐 |
| v1.0 | 2026-05 | 初版 API 目录 |
