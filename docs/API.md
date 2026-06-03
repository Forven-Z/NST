# 智慧云脑诊疗平台 — API 接口文档

> **版本**：v1.2 | 2026-05  
> **文档索引**：[README.md](./README.md)  
> **Base URL**：`http://{host}:9000`（经 Gateway，**禁止**前端直连微服务端口）  
> **路径→服务**：§〇（与 [MICROSERVICES.md](./MICROSERVICES.md) §四 一致）  
> **实施策略**：P1～P3 实现 Java 业务 API；§四～§五 标题为 **his 内逻辑模块名**，部署进程均为 **`hospital-his`**（检验/检查 API 逐步迁至 `/lis/**`、`/pacs/**`）。

---

## 〇、API 路径 → 微服务映射（定稿）

> 外部 URL **不变**（如 `/api/v1/patient/**`），由 Gateway 转发至下列 `spring.application.name`。

| 路径前缀 | 目标服务 | 端口 | API.md 章节 |
|----------|----------|------|-------------|
| `/api/v1/auth/**` | `hospital-auth` | 9101 | §三 |
| `/api/v1/patient/**` | `hospital-his` | 9102 | §四 |
| `/api/v1/doctor/**` | `hospital-his` | 9102 | §五 |
| `/api/v1/registrar/**` | `hospital-his` | 9102 | §五 / 收费 |
| `/api/v1/pharmacy/**` | `hospital-his` | 9102 | §五 药师 |
| `/api/v1/lis/**` | `hospital-lis` | 9103 | §五 检验（待拆专章） |
| `/api/v1/pacs/**` | `hospital-pacs` | 9104 | §五 检查（待拆专章） |
| `/api/v1/admin/**` | `hospital-management` | 9105 | §六 |
| `/api/v1/ai/**` | `hospital-ai-bridge` | 9106 | §七 |
| `/api/v1/callback/wechat/pay` | `hospital-his` | 9102 | §十 |
| `/internal/**` | 各服务 | — | **不经 Gateway** |

**内网**：`hospital-pacs` → `hospital-ai`（FastAPI `:8000`）；回调见 §八、§十。

---

## 目录

1. [通用约定](#一通用约定)
2. [认证与授权](#二认证与授权)
3. [hospital-auth](#三hospital-auth)
4. [患者端 API（his · patient 模块）](#四患者端-apihis--patient-模块)
5. [医护端 API（his / lis / pacs）](#五医护端-apihis--lis--pacs)
6. [hospital-management（管理端）](#六hospital-management管理端)
7. [hospital-ai-bridge（Spring AI · 预留）](#七hospital-ai-bridgespring-ai--预留)
8. [hospital-ai（Python CNN · 内网预留）](#八hospital-aipython-cnn--内网预留)
9. [服务间 Feign（内部）](#九服务间-feign内部)
10. [Webhook 与回调](#十webhook-与回调)
11. [错误码一览](#十一错误码一览)
12. [分期实施对照表](#十二分期实施对照表)

---

## 一、通用约定

### 1.1 路径与版本

| 项 | 值 |
|----|-----|
| 统一前缀 | `/api/v1` |
| 示例 | `POST /api/v1/patient/registers` |
| Content-Type | `application/json`（上传除外） |
| 字符编码 | UTF-8 |
| 时间格式 | ISO-8601，`2026-05-26T14:30:00+08:00` |

### 1.2 统一响应体 `Result<T>`

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| `code` | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未登录或 Token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 业务冲突（如重复挂号、状态不允许） |
| 500 | 服务器错误 |
| 50301 | AI 能力未启用（STUB 期） |
| 50302 | CNN 推理服务未启用（STUB 期） |

**分页列表** `data` 结构：

```json
{
  "list": [],
  "total": 100,
  "page": 1,
  "pageSize": 10
}
```

### 1.3 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 除白名单外必填 | `Bearer {accessToken}` |
| `X-Request-Id` | 否 | 链路追踪 UUID |
| `X-Client-Type` | 否 | `MINIAPP` / `PC_DOCTOR` / `PC_ADMIN` |

### 1.4 角色与 API 前缀

| 角色 | JWT `roles` | 可访问前缀 |
|------|-------------|------------|
| 患者 | `PATIENT` | `/api/v1/patient/**` |
| 挂号收费员 | `REGISTRAR` | `/patient/**`（收费查询）、`/admin/**` 收费子集 |
| 门诊医生 | `OUTPATIENT_DOCTOR` | `/api/v1/doctor/**` |
| 检查/检验/处置医生 | `CHECK_DOCTOR` 等 | `/doctor/**` 医技子集 |
| 药师 | `PHARMACIST` | `/api/v1/pharmacy/**`（发药、退药） |
| 管理员 | `ADMIN` | `/api/v1/admin/**` |

### 1.5 阶段标记

| 标记 | 含义 |
|------|------|
| **P1** | 首期必做（挂号、接诊、病历） |
| **P2** | 医技 + 收费 |
| **P3** | 处方 + 发药 |
| **P4** | Spring AI + CNN 影像 |
| **P5** | 智能排班 + Timefold |
| **STUB** | 接口已定义，返回占位或 50301/50302 |
| **预留** | 契约锁定，后期实现 |

---

## 二、认证与授权

### 2.1 Token 载荷（建议）

**患者 Token**

```json
{
  "sub": "10001",
  "type": "PATIENT",
  "patientId": 10001,
  "exp": 1716700000
}
```

**员工 Token**

```json
{
  "sub": "2001",
  "type": "STAFF",
  "userId": 2001,
  "employeeId": 88,
  "roles": ["OUTPATIENT_DOCTOR"],
  "exp": 1716700000
}
```

### 2.2 网关白名单（无需 Token）

- `POST /api/v1/auth/staff/login`
- `POST /api/v1/patient/auth/wechat`（**his** 入口，ADR-001 方案 C）
- `POST /api/v1/callback/wechat/pay`
- `GET /api/v1/auth/health`
- `GET /actuator/health`（各服务，内网）

---

## 三、hospital-auth（认证中心 · 统一签发 Token）

**服务端口**：9101 | **网关前缀**：`/api/v1/auth`  
> **ADR-001**：医护、患者 **accessToken 均只由本服务签发**；患者微信与落库在 **his**（§4.0）。

### 3.1 员工登录

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **Method / Path** | `POST /staff/login` |
| **权限** | 公开 |

**Request**

```json
{
  "username": "doctor01",
  "password": "******"
}
```

**Response `data`**

```json
{
  "accessToken": "eyJhbG...",
  "expiresIn": 7200,
  "userId": 2001,
  "employeeId": 88,
  "realName": "张医生",
  "roles": ["OUTPATIENT_DOCTOR"],
  "deptId": 10,
  "deptName": "内科"
}
```

---

### 3.2 内部 · 为患者签发 Token（仅 his 调用）

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **Method / Path** | `POST /internal/token/patient` |
| **权限** | **服务间**（不经 Gateway；his Feign 调用） |
| **调用方** | `hospital-his` |

**Request**

```json
{
  "patientId": 10001,
  "medicalRecordNo": "MR202605260001"
}
```

**Response `data`**

```json
{
  "accessToken": "eyJhbG...",
  "expiresIn": 7200,
  "tokenType": "Bearer"
}
```

> 患者对外登录请使用 **§4.0** `POST /api/v1/patient/auth/wechat`（his 完成微信与落库后调用本接口）。

---

### 3.3 刷新 Token

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **Method / Path** | `POST /token/refresh` |
| **权限** | 已登录 |

**Request**

```json
{
  "refreshToken": "..."
}
```

---

### 3.4 登出

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **Method / Path** | `POST /logout` |
| **权限** | 已登录 |

---

### 3.5 当前用户信息

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **Method / Path** | `GET /me` |
| **权限** | 已登录 |

---

## 四、患者端 API（his · patient 模块）

> **部署服务**：`hospital-his`（:9102）· Gateway：`/api/v1/patient/**`

**服务端口**：9102 | **网关前缀**：`/api/v1/patient`

### 4.0 微信登录（his 落库 + auth 签发）

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **Method / Path** | `POST /auth/wechat` |
| **权限** | 公开（网关白名单） |
| **完整 URL** | `POST /api/v1/patient/auth/wechat` |

**Request**

```json
{
  "code": "wx_login_code_from_miniapp",
  "nickName": "微信用户",
  "avatarUrl": "https://..."
}
```

**处理顺序（实现约束）**

1. his：调用微信 `code2session`，创建/更新 `patient`、`patient_wechat`
2. his：Feign → auth `POST /internal/token/patient`
3. his：将 Token 与业务字段组装为下列 Response

**Response `data`**

```json
{
  "accessToken": "eyJhbG...",
  "expiresIn": 7200,
  "patientId": 10001,
  "medicalRecordNo": "MR202605260001",
  "isNewPatient": false
}
```

> 首次登录：`isNewPatient=true`，引导补全 `PUT /api/v1/patient/profile`。Token 刷新走 auth `POST /api/v1/auth/token/refresh`（§3.3）。

---

### 4.1 患者档案

#### 4.1.1 获取本人档案

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/profile` |
| **权限** | PATIENT |

**Response `data`**

```json
{
  "id": 10001,
  "medicalRecordNo": "MR202605260001",
  "realName": "李四",
  "gender": 1,
  "birthDate": "1990-01-01",
  "phone": "13800000000",
  "idCard": "310...",
  "address": "上海市...",
  "settleCategoryId": 1,
  "settleCategoryName": "自费"
}
```

#### 4.1.2 完善/更新档案

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **PUT** | `/profile` |

**Request**：同档案字段（`medicalRecordNo` 不可改）。

---

### 4.2 挂号

#### 4.2.1 可挂号排班列表

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/schedules` |

**Query**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deptId | long | 否 | 科室 |
| workDate | date | 否 | 默认今天 |
| noonType | int | 否 | 1 上午 2 下午 |
| registLevelId | long | 否 | 号别 |

**Response `data.list[]`**

```json
{
  "schedulingId": 501,
  "deptId": 10,
  "deptName": "内科",
  "employeeId": 88,
  "doctorName": "张医生",
  "registLevelId": 1,
  "levelName": "普通号",
  "registFee": 20.00,
  "remainQuota": 15
}
```

#### 4.2.2 创建线上挂号（生成待缴单）

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **POST** | `/registers` |

**Request**

```json
{
  "schedulingId": 501,
  "deptId": 10,
  "employeeId": 88,
  "registLevelId": 1,
  "settleCategoryId": 1
}
```

**Response `data`**

```json
{
  "registerId": 3001,
  "billId": 8001,
  "billNo": "B202605260001",
  "amount": 20.00,
  "visitState": 0,
  "message": "请完成支付后进入已挂号状态"
}
```

> 支付成功前 `visit_state` 可为中间态或仍待支付；回调成功后置 **1 已挂号**。

#### 4.2.3 我的挂号列表

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/registers` |

**Query**：`page`, `pageSize`, `visitState`（可选）

#### 4.2.4 挂号详情

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/registers/{registerId}` |

---

### 4.3 费用与支付（按单付）

#### 4.3.1 我的待缴单

| 项 | 值 |
|----|-----|
| **阶段** | P1～P3 |
| **GET** | `/bills` |

**Query**：`status`（0 待支付）, `registerId`（可选，当次就诊）

**Response `data.list[]`**

```json
{
  "id": 8002,
  "billNo": "B202605260002",
  "bizType": "CHECK",
  "bizId": 6001,
  "billTitle": "头部 CT",
  "amount": 280.00,
  "status": 0,
  "registerId": 3001,
  "createTime": "2026-05-26T10:00:00+08:00"
}
```

#### 4.3.2 发起微信支付（单笔）

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **POST** | `/payments/wechat/prepay` |

**Request**

```json
{
  "billIds": [8001],
  "openid": "oXXXX"
}
```

**Response `data`**

```json
{
  "paymentId": 9001,
  "paymentNo": "P202605260001",
  "timeStamp": "1716700000",
  "nonceStr": "...",
  "package": "prepay_id=...",
  "signType": "RSA",
  "paySign": "..."
}
```

#### 4.3.3 查询支付结果

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/payments/{paymentId}` |

#### 4.3.4 我的缴费记录

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **GET** | `/payments` |

**Query**：`page`, `pageSize`, `registerId`

> **不可见**：他人流水、`operator` 内部备注（见 §2.3.1 权限）。

#### 4.3.5 我的退款记录

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **GET** | `/refunds` |

---

### 4.4 电子病历（只读）

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/medical-records` |

**Query**：`registerId` 或 `page`

| 项 | 值 |
|----|-----|
| **GET** | `/medical-records/{registerId}` |

**Response `data`**：病历字段见 `DATABASE_DESIGN.md` §5.2（脱敏规则按合规配置）。

---

### 4.5 检查/检验进度（患者查询）

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **GET** | `/registers/{registerId}/orders` |

**Response `data`**

```json
{
  "checks": [{ "id": 6001, "itemName": "头部 CT", "status": 20, "statusText": "已缴费" }],
  "inspections": [],
  "prescriptions": []
}
```

---

### 4.6 影像上传（患者端 · 迁移说明）

> **ADR-002 定稿**：正式实现为 **`POST /api/v1/pacs/imaging/upload`**（**hospital-pacs**），见 §五 pacs 章节（P3/P4 补充）。  
> 下列路径为 **历史占位**（Gateway 若仍路由到 his 则仅返回 STUB），**新开发勿用**。

| 项 | 值 |
|----|-----|
| **阶段** | P4 / **STUB**（旧路径） |
| **POST** | `/imaging/upload`（**将废弃**，改用 `/api/v1/pacs/...`） |
| **Content-Type** | `multipart/form-data` |

**Form**：`file`, `checkRequestId`, `modality`（`CT_HEAD` / `CT_LUNG`）

**STUB 响应**（P1～P3）

```json
{
  "code": 50302,
  "message": "CNN imaging service not enabled",
  "data": null
}
```

---

### 4.7 AI 智能问诊（预留 · P4）

| 项 | 值 |
|----|-----|
| **阶段** | P4 / **STUB** |
| **POST** | `/ai/triage/chat` |

前期：转发至 `hospital-ai-bridge` 的 STUB，或本服务直接返回 50301。

---

## 五、医护端 API（his / lis / pacs）

> **部署服务**：门诊/医嘱/药房 → `hospital-his`；检验 → `hospital-lis`；检查/影像 → `hospital-pacs`。下文部分路径仍写在 `/doctor/**`，实现期可并行提供 `/lis/**`、`/pacs/**`。

**服务端口**：9103 | **网关前缀**：`/api/v1/doctor`

### 5.1 患者队列与叫号

#### 5.1.1 今日挂号患者列表

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/registers/queue` |
| **权限** | OUTPATIENT_DOCTOR |

**Query**

| 参数 | 说明 |
|------|------|
| visitState | 1 已挂号 / 2 接诊中 |
| keyword | 病历号/姓名 |
| page, pageSize | 分页 |

**Response `data.list[]`**

```json
{
  "registerId": 3001,
  "medicalRecordNo": "MR202605260001",
  "patientName": "李四",
  "gender": 1,
  "age": 36,
  "visitState": 1,
  "registTime": "2026-05-26T08:30:00+08:00",
  "registLevelName": "普通号"
}
```

#### 5.1.2 叫号（开始接诊）

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **POST** | `/registers/{registerId}/call` |

**Response**：`visitState` → 2 医生接诊。

#### 5.1.3 结束看诊

| 项 | 值 |
|----|-----|
| **阶段** | P3 |
| **POST** | `/registers/{registerId}/finish` |

**说明**：开立处方或处置后可置 `visitState=3`；也可在开立处方时自动结束（与业务配置一致）。

---

### 5.2 病历

#### 5.2.1 获取病历

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **GET** | `/registers/{registerId}/medical-record` |

#### 5.2.2 保存病历（创建/更新）

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **PUT** | `/registers/{registerId}/medical-record` |

**Request**

```json
{
  "readme": "头痛三天",
  "present": "持续性钝痛...",
  "presentTreat": "",
  "history": "",
  "allergy": "青霉素过敏",
  "physique": "T 36.5℃...",
  "diagnosis": "偏头痛？",
  "cure": "建议进一步检查",
  "checkAdvice": "头部 CT",
  "inspectionAdvice": "",
  "diseaseIds": [101, 102]
}
```

#### 5.2.3 确诊提交

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **POST** | `/registers/{registerId}/medical-record/confirm` |

**Request**：`diagnosis`, `cure`, `diseaseIds`

---

### 5.3 检查申请

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **GET** | `/check-requests` |
| **Query** | `registerId`, `status` |

| 项 | 值 |
|----|-----|
| **POST** | `/check-requests` |

**Request**

```json
{
  "registerId": 3001,
  "medicalTechnologyId": 201,
  "purpose": "排除颅内占位",
  "bodyPart": "头部",
  "remark": "",
  "fromAi": false
}
```

**Response**：`id`, `status=10`（已开立）, `billId`（自动生成待缴单）。

| 项 | 值 |
|----|-----|
| **GET** | `/check-requests/{id}` |

| 项 | 值 |
|----|-----|
| **GET** | `/check-requests/{id}/result` |

**Response**：`resultText`, `resultAttachment`（仅 **status≥40**）。

> 医生接口 **不返回** `payment_record` 明细，仅 `paid: true/false` 或 `status`。

---

### 5.4 检验申请

路径与检查对称，前缀 `/inspection-requests`。**阶段 P2**。

---

### 5.5 处置申请

前缀 `/disposal-requests`。**阶段 P2～P3**。

---

### 5.6 处方

#### 5.6.1 AI 处方草稿（预留）

| 项 | 值 |
|----|-----|
| **阶段** | P4 STUB / P3 可手工跳过 |
| **POST** | `/prescriptions/ai-draft` |

**Request**

```json
{
  "registerId": 3001
}
```

**STUB `data`**

```json
{
  "stub": true,
  "draftId": null,
  "message": "AI prescription draft disabled in P1-P3"
}
```

#### 5.6.2 保存 AI 草稿（医生编辑）

| 项 | 值 |
|----|-----|
| **阶段** | P3 |
| **PUT** | `/prescriptions/ai-draft/{draftId}` |

#### 5.6.3 确认提交处方（已开立）

| 项 | 值 |
|----|-----|
| **阶段** | P3 |
| **POST** | `/prescriptions` |

**Request**

```json
{
  "registerId": 3001,
  "draftId": 7001,
  "items": [
    {
      "drugId": 1001,
      "quantity": 2,
      "usageMethod": "口服",
      "dosage": "0.5g",
      "frequency": "tid",
      "days": 7,
      "entrust": "饭后服用"
    }
  ],
  "remark": ""
}
```

**Response**：`prescriptionId`, `prescriptionNo`, `totalAmount`, `status=10`, `billId`。

#### 5.6.4 处方列表/详情

| **GET** | `/prescriptions?registerId=` |
| **GET** | `/prescriptions/{id}` |

---

### 5.7 医技科室（检查/检验/处置执行）

> 检验接口路由至 `hospital-lis`（`/api/v1/lis/**`）；检查路由至 `hospital-pacs`（`/api/v1/pacs/**`）。

#### 5.7.1 待执行列表

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **GET** | `/tech/check/pending` |
| **权限** | CHECK_DOCTOR |

**Query**：`status=20`（已缴费）

#### 5.7.2 开始检查（执行完成）

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **POST** | `/tech/check/{id}/execute` |

#### 5.7.3 录入检查结果

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **POST** | `/tech/check/{id}/result` |

**Request**

```json
{
  "resultText": "未见明显异常",
  "resultAttachment": "minio://bucket/key/report.pdf"
}
```

**Response**：`status=40` 已出结果。

检验、处置路径：`/tech/inspection/...`、`/tech/disposal/...`。

---

### 5.8 药房发药

| 项 | 值 |
|----|-----|
| **阶段** | P3 |
| **GET** | `/pharmacy/pending` |
| **权限** | PHARMACIST |

**Query**：`status=20`（处方已缴费未发药）

| 项 | 值 |
|----|-----|
| **POST** | `/pharmacy/prescriptions/{id}/dispense` |

**Response**：处方 `status=30` 已发药。

| 项 | 值 |
|----|-----|
| **POST** | `/pharmacy/prescriptions/{id}/return-drug` |

退药链：→ 已退药 → 触发退费流程（管理端/收费员）。

---

### 5.9 窗口收费（挂号收费员在 PC 的操作）

> 实现于 `hospital-his` 的 `registrar` 模块；网关路径 `/api/v1/registrar/**` 或 `/api/v1/admin/charge/**`。

#### 5.9.1 按病历号查患者待缴

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **GET** | `/charge/patient/{medicalRecordNo}/bills` |
| **权限** | REGISTRAR |

#### 5.9.2 窗口收费结算

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **POST** | `/charge/settle` |

**Request**

```json
{
  "patientId": 10001,
  "billIds": [8001, 8002],
  "channel": "CASH",
  "receivedAmount": 300.00,
  "remark": ""
}
```

#### 5.9.3 窗口退费

| 项 | 值 |
|----|-----|
| **阶段** | P2 |
| **POST** | `/charge/refund` |

**Request**

```json
{
  "billId": 8002,
  "paymentId": 9001,
  "refundAmount": 280.00,
  "reason": "患者取消检查"
}
```

#### 5.9.4 窗口挂号

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **POST** | `/charge/register` |

**Request**：患者信息 + 排班 + 当场 `channel=CASH` 收挂号费。

#### 5.9.5 退号

| 项 | 值 |
|----|-----|
| **阶段** | P1 |
| **POST** | `/charge/register/{registerId}/cancel` |

**说明**：原路退款，见 `refund_record`。

---

## 六、hospital-management（管理端）

**服务端口**：9104 | **网关前缀**：`/api/v1/admin`

### 6.1 基础数据 CRUD（P1～P2）

| 资源 | GET 列表 | GET 详情 | POST | PUT | DELETE |
|------|----------|----------|------|-----|--------|
| 科室 `departments` | `/departments` | `/{id}` | ✓ | ✓ | 逻辑删 |
| 员工 `employees` | `/employees` | `/{id}` | ✓ | ✓ | ✓ |
| 挂号级别 `regist-levels` | ✓ | ✓ | ✓ | ✓ | ✓ |
| 结算类别 `settle-categories` | ✓ | ✓ | ✓ | ✓ | ✓ |
| 医技项目 `medical-technologies` | ✓ | ✓ | ✓ | ✓ | ✓ |
| 药品 `drugs` | ✓ | ✓ | ✓ | ✓ | ✓ |
| 疾病 `diseases` | ✓ | ✓ | ✓ | ✓ | ✓ |

**Query 公共**：`page`, `pageSize`, `keyword`, `delmark=0`

---

### 6.2 排班（P1 手工 / P5 智能）

#### 6.2.1 排班列表

| 项 | 值 |
|----|-----|
| **GET** | `/schedules` |

**Query**：`workDate`, `deptId`, `employeeId`

#### 6.2.2 创建/发布排班

| 项 | 值 |
|----|-----|
| **POST** | `/schedules` |
| **PUT** | `/schedules/{id}` |
| **POST** | `/schedules/{id}/publish` |

**Request 示例**

```json
{
  "deptId": 10,
  "employeeId": 88,
  "registLevelId": 1,
  "workDate": "2026-05-27",
  "noonType": 1,
  "totalQuota": 30
}
```

#### 6.2.3 AI 排班建议（预留 · P5）

| 项 | 值 |
|----|-----|
| **阶段** | P5 STUB |
| **POST** | `/schedules/ai-suggest` |

#### 6.2.4 Timefold 求解（预留 · P5）

| 项 | 值 |
|----|-----|
| **阶段** | P5 |
| **POST** | `/schedules/solve` |

---

### 6.3 费用统计（P3+）

| 项 | 值 |
|----|-----|
| **GET** | `/reports/payments/summary` |
| **权限** | ADMIN |

**Query**：`startDate`, `endDate`, `deptId`, `channel`

**Response**：按日/渠道汇总金额笔数（**不含**患者隐私明细导出）。

---

### 6.4 系统用户

| 项 | 值 |
|----|-----|
| **GET/POST/PUT** | `/users` |
| **POST** | `/users/{id}/reset-password` |

---

## 七、hospital-ai-bridge（Spring AI · 预留）

**服务端口**：9105 | **网关前缀**：`/api/v1/ai`  
**前期策略**：接口 **全部注册**；实现返回 **50301** 或 `stub: true` 占位 JSON，**不阻塞** Java 主流程。

### 7.1 健康检查

| **GET** | `/health` | 公开（内网） |

---

### 7.2 患者智能问诊（SSE）

| 项 | 值 |
|----|-----|
| **阶段** | P4 STUB |
| **POST** | `/triage/chat` |
| **Accept** | `text/event-stream` |

**Request**

```json
{
  "patientId": 10001,
  "registerId": null,
  "message": "我头痛三天了",
  "sessionId": "optional-uuid"
}
```

**STUB 事件流**

```text
data: {"stub":true,"delta":"【占位】您好，请先完成挂号后就诊。"}
```

---

### 7.3 医生 AI 助理（SSE）

| 项 | 值 |
|----|-----|
| **阶段** | P4 STUB |
| **POST** | `/assistant/stream` |

**Request**

```json
{
  "registerId": 3001,
  "message": "根据病历给出鉴别诊断",
  "context": { "medicalRecordId": 5001 }
}
```

---

### 7.4 辅助诊断建议

| 项 | 值 |
|----|-----|
| **阶段** | P4 STUB |
| **POST** | `/diagnosis/suggest` |

**Request**：`registerId`, `symptomsSummary`

**Response STUB**

```json
{
  "stub": true,
  "suggestions": [],
  "needCheck": true,
  "needInspection": false,
  "reason": "AI module disabled"
}
```

---

### 7.5 处方草稿生成

| 项 | 值 |
|----|-----|
| **阶段** | P4 STUB |
| **POST** | `/prescription/draft` |

**Request**：`registerId`, `diagnosis`

**Response**：与 `ai_prescription_draft.draft_content` JSON 结构一致。

---

### 7.6 排班自然语言建议（P5）

| 项 | 值 |
|----|-----|
| **POST** | `/scheduling/suggest` |

---

### 7.7 RAG 知识库管理（P4+）

| **POST** | `/rag/documents` | 上传文档切片 |
| **DELETE** | `/rag/documents/{id}` | |
| **POST** | `/rag/search` | 检索测试 |

---

## 八、hospital-ai（Python CNN · 内网预留）

**Base URL（内网）**：`http://hospital-ai:8000`  
**调用方**：`hospital-pacs`（异步任务），**不经 Gateway 对外暴露**。

### 8.1 通用约定

| Header | 值 |
|--------|-----|
| `X-Internal-Token` | 集群内共享密钥（Nacos 配置） |
| Content-Type | `application/json` |

**统一响应**

```json
{
  "success": true,
  "code": 0,
  "message": "ok",
  "data": { }
}
```

**前期 STUB**：`success=false`, `code=50302`, `message="CNN service not implemented"`

---

### 8.2 健康检查

| **GET** | `/v1/health` |

---

### 8.3 提交推理任务（异步 · 定稿）

| 项 | 值 |
|----|-----|
| **阶段** | P4 预留 |
| **POST** | `/v1/inference/jobs` |

**Request**

```json
{
  "studyId": 10001,
  "studyNo": "IMG202605260001",
  "modality": "CT_HEAD",
  "sourceBucket": "hospital-imaging",
  "sourceObjectKey": "patient/10001/ct/raw.dcm",
  "callbackUrl": "http://hospital-pacs:9104/internal/imaging/callback"
}
```

**Response**

```json
{
  "success": true,
  "data": {
    "jobId": "job-uuid",
    "status": "PENDING"
  }
}
```

---

### 8.4 查询任务状态

| **GET** | `/v1/inference/jobs/{jobId}` |

**Response `data`**

```json
{
  "jobId": "job-uuid",
  "status": "COMPLETED",
  "reportJson": { },
  "resultBucket": "hospital-imaging",
  "resultObjectKey": "patient/10001/ct/seg.png"
}
```

---

### 8.5 同步推理（仅调试 · 可选）

| **POST** | `/v1/inference/sync` |

> 生产环境禁止大图同步；仅开发联调。

---

### 8.6 模型路由（预留）

| Modality | Path | 说明 |
|----------|------|------|
| `CT_HEAD` | `/v1/models/ct-head/infer` | 头部 CT 伪影/识别 |
| `CT_LUNG` | `/v1/models/ct-lung/infer` | 肺部 CT |
| `TUMOR_SEG` | `/v1/models/tumor-segment/infer` | 肿瘤分割 |

> 对外推荐 **统一走** `/v1/inference/jobs`；上表为 Python 内部子路由，后期优化时实现。

---

### 8.7 Java 回调（patient 服务接收）

| 项 | 值 |
|----|-----|
| **阶段** | P4 |
| **POST** | `http://hospital-pacs:9104/internal/imaging/callback` |
| **权限** | `X-Internal-Token` |

**Request**

```json
{
  "studyId": 10001,
  "status": "COMPLETED",
  "reportJson": { },
  "resultBucket": "hospital-imaging",
  "resultObjectKey": "..."
}
```

---

## 九、服务间 Feign（内部）

| 调用方 | 被调方 | 接口示例 | 阶段 |
|--------|--------|----------|------|
| **his** | **auth** | `POST /internal/token/patient` | P1 |
| his | lis | 开立检验后通知（可选） | P2 |
| his | pacs | 开立检查后通知（可选） | P3 |
| lis / pacs | his | 读挂号/患者摘要（可选 Feign） | P2+ |
| pacs | hospital-ai | `POST /v1/inference/jobs` | P4 |
| his | ai-bridge | `POST /internal/ai/...` | P4 STUB |
| management | his | 统计看诊量 | P3+ |

> 内部接口前缀 `/internal/**`，网关 **禁止** 路由到公网。

---

## 十、Webhook 与回调

### 10.1 微信支付结果通知

| 项 | 值 |
|----|-----|
| **POST** | `/api/v1/callback/wechat/pay` |
| **权限** | 微信服务器签名验证 |
| **处理** | 更新 `payment_record`、`bill`、业务单状态、`register.visit_state` |

### 10.2 微信退款通知（P2+）

| **POST** | `/api/v1/callback/wechat/refund` |

---

## 十一、错误码一览

| code | message 示例 | 场景 |
|------|----------------|------|
| 200 | success | |
| 40001 | 参数缺失 | |
| 40002 | 病历号不存在 | |
| 40101 | Token 无效 | |
| 40301 | 无权访问该患者数据 | 越权 |
| 40901 | 号源已满 | 挂号 |
| 40902 | 当前状态不允许缴费 | 状态机 |
| 40903 | 未缴费不可执行检查 | |
| 40904 | 未缴费不可发药 | |
| 50001 | 微信支付下单失败 | |
| 50301 | AI 能力未启用 | ai-bridge STUB |
| 50302 | CNN 服务未启用 | hospital-ai STUB |

---

## 十二、分期实施对照表

| 阶段 | 必须实现的 API 域 | Python/LLM |
|------|-------------------|------------|
| **P1** | auth、patient 档案/挂号/微信付、doctor 队列/病历、admin 基础数据/排班、窗口挂号 | 无 |
| **P2** | 检查/检验/处置开立与执行、bill/payment/refund、窗口收费 | 无 |
| **P3** | 处方、发药、退药退费、确诊、统计报表基础 | 无 |
| **P4** | 影像上传链路、ai-bridge SSE、CNN jobs | **重点优化** |
| **P5** | 排班 AI 建议 + Timefold solve | LLM 增强 |

---

## 附录 A：状态字段 API 表示建议

对外同时返回数字与文案，便于前端：

```json
{
  "status": 20,
  "statusText": "已缴费",
  "paid": true
}
```

医技/处方 `status` 枚举见 `DATABASE_DESIGN.md` §1.5。

---

## 附录 B：OpenAPI 导出（后续）

实现阶段可使用 SpringDoc 按 Controller 生成 `openapi.yaml`，与本文件 diff 校验一致性。

---

*文档版本：v1.2 | ADR-001 方案 C：§4.0 患者微信登录（his）；§3.2 internal 签发 Token（auth）。*
