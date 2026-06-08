# 智慧云脑诊疗平台 — API 接口文档（唯一契约）

> **版本**：v2.0 | 2026-06-04  
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
| `POST /doctor/medical-records/{registerId}/submit` | `/doctor/registers/{id}/medical-record/submit` |
| `POST /doctor/registers/{registerId}/finish` | `/doctor/registers/{id}/finish`（同义，定稿保留 registers 段） |
| `GET /admin/scheduling` | `GET /admin/schedules` |
| `POST/PUT /admin/scheduling` | `POST/PUT /admin/schedules` |
| `POST /pharmacy/prescriptions/{id}/dispense` | `/pharmacy/dispense/{id}` |
| `POST /patient/payments`（Mock 批量支付） | 首期演示用；真微信支付见 §4.3.2 预留 |
| 医技执行 | `GET/POST /lis/**`、`/pacs/**`、`/disposal/**`（非 `/doctor` 下执行） |

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
| P4/P5 | 分期实现，不阻塞 P1～P3 |

### 1.4 Mock 开关

| 工程 | 配置 | 默认 |
|------|------|------|
| PC `hospital-frontend` | `VITE_USE_MOCK=true` | Mock |
| 小程序 | `config.js` → `USE_MOCK=true` | Mock |

Mock 数据结构 **与本文件一致**。

### 1.5 网关白名单（无需 Token）

- `POST /auth/staff/login`
- `POST /patient/auth/wechat`
- `POST /callback/wechat/pay`
- `GET /auth/health`、各服务 `/health`

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
| ✅ | P1 | POST | `/patient/auth/wechat` | his | 公开 |
| ✅ | P1 | GET/PUT | `/patient/profile` | his | PATIENT |
| ✅ | P1 | GET/POST | `/patient/family-members` | his | PATIENT |
| ✅ | P1 | GET | `/patient/departments` | his | PATIENT |
| ✅ | P1 | GET | `/patient/schedules` | his | PATIENT |
| ✅ | P1 | POST | `/patient/registers` | his | PATIENT |
| ✅ | P1 | GET | `/patient/registers` | his | PATIENT |
| ✅ | P1 | GET | `/patient/registers/{registerId}` | his | PATIENT |
| ✅ | P1 | GET | `/patient/registers/{registerId}/queue-status` | his | PATIENT |
| ✅ | P1 | POST | `/patient/registers/{registerId}/cancel` | his | PATIENT |
| ⬜ | P2 | GET | `/patient/registers/{registerId}/orders` | his | PATIENT |
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
| ⬜ | P3 | POST | `/doctor/registers/{registerId}/finish` | his | OUTPATIENT_DOCTOR |
| ✅ | P1 | GET/PUT | `/doctor/medical-records/{registerId}` | his | OUTPATIENT_DOCTOR |
| ⬜ | P1 | POST | `/doctor/medical-records/{registerId}/submit` | his | OUTPATIENT_DOCTOR |
| ⬜ | P2 | GET | `/doctor/registers/{registerId}/orders` | his | OUTPATIENT_DOCTOR |
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
| ✅ | P3 | GET | `/pacs/queue` | pacs | CHECK_DOCTOR |
| ✅ | P3 | POST | `/pacs/requests/{id}/execute` | pacs | CHECK_DOCTOR |
| ✅ | P3 | POST | `/pacs/requests/{id}/result` | pacs | CHECK_DOCTOR |
| ⬜ | P3 | GET | `/pacs/imaging-studies` | pacs | CHECK_DOCTOR |
| STUB | P4 | POST | `/pacs/imaging/upload` | pacs | CHECK_DOCTOR |
| ✅ | P3 | GET | `/disposal/queue` | disposal | DISPOSAL_DOCTOR |
| ✅ | P3 | POST | `/disposal/requests/{id}/execute` | disposal | DISPOSAL_DOCTOR |
| ✅ | P3 | POST | `/disposal/requests/{id}/result` | disposal | DISPOSAL_DOCTOR |
| ✅ | P3 | GET | `/pharmacy/pending` | his | PHARMACIST |
| ✅ | P3 | POST | `/pharmacy/prescriptions/{id}/dispense` | his | PHARMACIST |
| ✅ | P3 | POST | `/pharmacy/prescriptions/{id}/return-drug` | his | PHARMACIST |
| ⬜ | P2 | POST | `/registrar/registers` | his | REGISTRAR |
| ⬜ | P2 | POST | `/registrar/charges` | his | REGISTRAR |
| ✅ | P2 | POST | `/registrar/refunds` | his | REGISTRAR |
| ✅ | P2 | GET | `/registrar/patients/{medicalRecordNo}/bills` | his | REGISTRAR |
| ✅ | P2 | POST | `/registrar/registers/{registerId}/cancel` | his | REGISTRAR |
| ✅ | P1 | GET | `/admin/departments` 等字典 | mgmt | ADMIN |
| ⬜ | P1 | GET | `/admin/scheduling` | mgmt | ADMIN |
| ⬜ | P1 | GET | `/admin/employees` | mgmt | ADMIN/REGISTRAR |
| ⬜ | P1+ | POST/PUT/DELETE | `/admin/**` CRUD | mgmt | ADMIN |
| STUB | P4 | POST | `/ai/triage/chat` | ai-bridge | PATIENT |
| STUB | P4 | POST | `/ai/assistant/stream` | ai-bridge | DOCTOR |
| STUB | P4 | POST | `/ai/diagnosis/suggest` | ai-bridge | DOCTOR |

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

### GET `/patient/registers/{registerId}/orders` ⬜ P2

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

| kind | 表 | status |
|------|-----|--------|
| inspection | `inspection_request` | 10/20/30/40/50 |
| check | `check_request` | 同上 |
| disposal | `disposal_request` | 同上 |
| prescription | `prescription` | 10/20/30/40/50 |

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

### GET `/doctor/queues` ✅ P1

**Query**：`visitState?`, `keyword?`, `page`, `pageSize`  
**Response `data.list[]`**：`registerId`, `medicalRecordNo`, `patientName`, `gender`, `age`, `visitState`, `registLevelName`, `noonLabel`

### POST `/doctor/call/{registerId}` ✅ P1

**效果**：`register.visit_state` → 2

### POST `/doctor/registers/{registerId}/finish` ⬜ P3

**效果**：`visit_state` → 3

### GET/PUT `/doctor/medical-records/{registerId}` ✅ P1

**PUT Request 字段**：`readme`, `present`, `presentTreat`, `history`, `allergy`, `physique`, `diagnosis`, `cure`, `checkAdvice`, `inspectionAdvice`, `diseaseIds?`

### POST `/doctor/medical-records/{registerId}/submit` ⬜ P1

**效果**：`medical_record.status` → 2，患者端可见

### GET `/doctor/registers/{registerId}/orders` ⬜ P2

**Response**：与 §4 `GET /patient/registers/{id}/orders` **相同**

### POST `/doctor/check-requests` ✅ P2

**Request**：`{ "registerId", "medicalTechnologyId", "purpose", "bodyPart", "remark?" }`  
**Response `data`**：`checkRequestId`, `itemName`, `status`（10）, `billId`, `message`

### POST `/doctor/inspection-requests` ✅ P2

同上，`inspectionRequestId`, `bizType=INSPECTION`

### POST `/doctor/disposal-requests` ✅ P3

同上，`disposalRequestId`, `bizType=DISPOSAL`

### GET `/doctor/check-requests/{id}/result` ✅

### GET `/doctor/inspection-requests/{id}/result` ✅

### GET `/doctor/disposal-requests/{id}/result` ✅

**前置**：`status >= 40`  
**Response `data`**：`*RequestId`, `itemName`, `resultText`, `reportTime`

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

**Response `data`**：`prescriptionId`, `totalAmount`, `status`（10）

### AI 辅助开单（P4 · ADR-015）STUB/⬜

| Method | 路径 | 说明 |
|--------|------|------|
| POST | `/ai/diagnosis/suggest` | 分支建议（ai-bridge） |
| POST | `/doctor/check-requests/ai-draft` | 生成检查草稿 |
| PUT | `/doctor/check-requests/ai-draft/{draftId}` | 编辑草稿 |
| POST | `/doctor/check-requests/ai-draft/{draftId}/confirm` | 确认提交 |
| POST | `/doctor/inspection-requests/ai-draft` | 检验（对称） |
| PUT | `/doctor/inspection-requests/ai-draft/{draftId}` | |
| POST | `/doctor/inspection-requests/ai-draft/{draftId}/confirm` | |
| POST | `/doctor/disposal-requests/ai-draft` | 处置（对称） |
| PUT | `/doctor/disposal-requests/ai-draft/{draftId}` | |
| POST | `/doctor/disposal-requests/ai-draft/{draftId}/confirm` | |
| POST | `/doctor/prescriptions/ai-draft` | 处方草稿 P4 |
| PUT | `/doctor/prescriptions/ai-draft/{draftId}` | |
| POST | `/doctor/prescriptions/ai-draft/{draftId}/confirm` | |

**流程**：病历保存 → `diagnosis/suggest` → ai-draft 三步 → 医生确认 → `status=10`；**禁止** AI 未经确认直接开立。

---

## 六、医技执行

### LIS · `/lis/**` · hospital-lis :9103

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/lis/health` | 健康检查 |
| ✅ | GET | `/lis/queue?status=20` | 待执行队列 |
| ✅ | POST | `/lis/requests/{id}/execute` | status→30 |
| ✅ | POST | `/lis/requests/{id}/result` | `{ "resultText" }` → status 40 |

**队列项**：`inspectionRequestId`, `medicalRecordNo`, `patientName`, `itemName`, `itemPrice`, `status`

### PACS · `/pacs/**` · hospital-pacs :9104

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/pacs/health` | |
| ✅ | GET | `/pacs/queue?status=20` | `checkRequestId` |
| ✅ | POST | `/pacs/requests/{id}/execute` | |
| ✅ | POST | `/pacs/requests/{id}/result` | 可含 `resultAttachment` |
| ⬜ | GET | `/pacs/imaging-studies` | 影像任务列表 |
| STUB | POST | `/pacs/imaging/upload` | 影像上传 |

**imaging-studies 列表项**：`studyId`, `checkRequestId`, `patientName`, `itemName`, `modality`, `status`, `uploadStatus`, `resultReady`

### Disposal · `/disposal/**` · hospital-disposal :9105

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/disposal/health` | |
| ✅ | GET | `/disposal/queue?status=20` | `disposalRequestId` |
| ✅ | POST | `/disposal/requests/{id}/execute` | |
| ✅ | POST | `/disposal/requests/{id}/result` | `{ "resultText" }` |

---

## 七、药房 · `/pharmacy/**`（his）

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ✅ | GET | `/pharmacy/pending?status=20` | 待发药处方 |
| ✅ | POST | `/pharmacy/prescriptions/{id}/dispense` | status→30 |
| ✅ | POST | `/pharmacy/prescriptions/{id}/return-drug` | 退药 → 窗口退费 |

---

## 八、收费员 · `/registrar/**`（his）

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| ⬜ | POST | `/registrar/registers` | 窗口挂号 + 当场收挂号费 |
| ⬜ | POST | `/registrar/charges` | `{ "billIds": [], "channel": "CASH", ... }` |
| ✅ | POST | `/registrar/refunds` | `{ "billId", "reason" }` |
| ✅ | GET | `/registrar/patients/{medicalRecordNo}/bills` | 按病历号查账 |
| ✅ | POST | `/registrar/registers/{registerId}/cancel` | 窗口退号 |

**窗口挂号 Request 示例**：患者信息 + `schedulingId` + `channel=CASH` + `receivedAmount`

---

## 九、管理端 · `/admin/**` · hospital-management :9107

### 9.1 字典只读 ✅ P1

| Method | 路径 | 表 |
|--------|------|-----|
| GET | `/admin/health` | — |
| GET | `/admin/departments` | `department` |
| GET | `/admin/departments/{id}` | |
| GET | `/admin/regist-levels` | `regist_level` |
| GET | `/admin/settle-categories` | `settle_category` |
| GET | `/admin/medical-technologies` | `medical_technology` |
| GET | `/admin/drugs` | `drug_info` |
| GET | `/admin/diseases` | `disease` |

**Query 公共**：`page`, `pageSize`, `keyword`  
**医技 `list[]`**：`id`, `itemCode`, `itemName`, `techType`（CHECK/INSPECTION/DISPOSAL）, `price`

### 9.2 排班 ⬜ P1/P5

| Method | 路径 | 说明 |
|--------|------|------|
| ⬜ GET | `/admin/scheduling` | 排班列表 |
| ⬜ POST | `/admin/scheduling` | 创建 |
| ⬜ PUT | `/admin/scheduling/{id}` | 更新 |
| ⬜ POST | `/admin/scheduling/{id}/publish` | 发布 |
| P5 | POST | `/admin/scheduling/ai-suggest` | Timefold 建议 |
| P5 | POST | `/admin/scheduling/solve` | 求解 |

**Query**：`workDate`, `deptId`, `employeeId`  
**Request 示例**：`{ "deptId", "employeeId", "registLevelId", "workDate", "noonType", "totalQuota" }`  
> `scheduling` 表不存 `dept_id`；出诊科室经 `employee.dept_id` 推导（DATABASE v1.14）。

### 9.3 员工 ⬜ P1

| Method | 路径 | 说明 |
|--------|------|------|
| ⬜ GET | `/admin/employees` | Query: `deptId`, `roleType` |
| ⬜ CRUD | `/admin/employees/{id}` | 管理端维护 |

### 9.4 字典 CRUD 写操作 ⬜ P2

各资源 `POST` / `PUT` / `DELETE`（逻辑删）与只读路径对称，见 §9.1 资源名。

---

## 十、hospital-ai-bridge · `/ai/**` :9106

| 状态 | Method | 路径 | 说明 |
|------|--------|------|------|
| STUB | GET | `/ai/health` | |
| STUB | POST | `/ai/triage/chat` | 患者问诊 SSE |
| STUB | POST | `/ai/assistant/stream` | 医生助理 SSE |
| STUB | POST | `/ai/diagnosis/suggest` | ADR-015 分支建议 |
| P4 | POST | `/ai/prescription/draft` | 处方草稿 |
| P5 | POST | `/ai/scheduling/suggest` | 排班 NL 建议 |

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
| 登录 | `POST /patient/auth/wechat` |
| 按科室挂号 | `GET /patient/departments`, `/schedules`, `POST /registers` |
| 按疾病导诊 | 本地规则 → 跳转挂号页 |
| 待缴/已缴 | `GET /patient/bills`, `POST /payments`, `GET /payments` |
| 挂号记录/排队/退号 | `GET /registers`, `/registers/{id}`, `/queue-status`, `POST .../cancel` |
| 就诊人 | `GET/POST /patient/family-members` |
| 电子病历 | `GET /patient/medical-records/{registerId}` |
| 报告 Tab | `GET /patient/reports`, `/reports/{type}/{id}` |
| 医嘱进度 | `GET /patient/registers/{id}/orders` |
| AI 问诊 P4 | `POST /ai/triage/chat` |

### PC 端

| 角色 | 页面 | 主要接口 |
|------|------|----------|
| 医生 | 工作台 | `/doctor/queues`, `/call/{id}`, `/medical-records/*`, `/*-requests`, `/registers/{id}/orders`, `/registers/{id}/finish` |
| 医生 | AI 7:3 | `/ai/diagnosis/suggest`, `/ai/assistant/stream`, `/*/ai-draft/**` |
| LIS/PACS/处置 | 队列 | `/lis/**`, `/pacs/**`, `/disposal/**` |
| PACS | 影像任务 | `GET /pacs/imaging-studies` |
| 药师 | 发药 | `/pharmacy/pending`, `.../dispense`, `.../return-drug` |
| 收费员 | 挂号/收费/退费 | `/registrar/registers`, `/charges`, `/refunds`, `/patients/{mrn}/bills` |
| 管理员 | 字典/排班 | `GET /admin/*`, `GET /admin/scheduling` |

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

## 附录 D · 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-04 | **合并**原 `API.md` + `API_INTERFACE_SPEC.md` 为唯一契约；**统一路径**（§〇）；增加实现状态总览 |
| v1.4 | 2026-06 | DATABASE v1.14 对齐 |
| v1.0 | 2026-05 | 初版 API 目录 |
