# 智慧云脑诊疗平台 — 前端页面与 API 对照

> **用途**：前后端并行开发、联调对表。  
> **Base URL**：`http://localhost:9000/api/v1`（Gateway）  
> **版本**：v1.0 | 2026-05  
> **完整契约**：[API.md](./API.md)

---

## 一、患者微信小程序 `hospital-patient-miniapp/`

| 页面（建议路径） | 阶段 | Method | API 路径 | 服务 |
|------------------|------|--------|----------|------|
| 登录 | P1 | POST | `/patient/auth/wechat` | his |
| 完善档案 | P1 | GET | `/patient/profile` | his |
| 完善档案 | P1 | PUT | `/patient/profile` | his |
| 挂号-选排班 | P1 | GET | `/patient/schedules` | his |
| 挂号-提交 | P1 | POST | `/patient/registers` | his |
| 待缴列表 | P1 | GET | `/patient/bills` | his |
| 支付（模拟） | P1 | POST | `/patient/payments` | his |
| 支付状态 | P1 | GET | `/patient/payments/{id}/status` | his |
| 我的挂号 | P1 | GET | `/patient/registers` | his |
| 电子病历 | P1 | GET | `/patient/medical-records` | his |
| 病历详情 | P1 | GET | `/patient/medical-records/{registerId}` | his |
| 医嘱进度 | P2 | GET | `/patient/registers/{registerId}/orders` | his |
| 影像上传 | P3/P4 | POST | **`/pacs/imaging/upload`** | **pacs**（非 `/patient/imaging/upload`） |
| AI 问诊 | P4 | POST/SSE | `/ai/triage/chat` | ai-bridge |

**请求封装**：`utils/request.js` 统一加 `Authorization: Bearer {patientToken}`。

---

## 二、PC 端 `hospital-frontend/`（按角色菜单）

### 2.1 公共

| 页面 | 阶段 | Method | API | 服务 |
|------|------|--------|-----|------|
| 登录 | P1 | POST | `/auth/staff/login` | auth |
| 刷新 Token | P1 | POST | `/auth/token/refresh` | auth |
| 当前用户 | P1 | GET | `/auth/me` | auth |

### 2.2 门诊医生 `role: OUTPATIENT_DOCTOR`

| 页面 | 阶段 | Method | API | 服务 |
|------|------|--------|-----|------|
| 患者队列 | P1 | GET | `/doctor/queues` | his |
| 叫号 | P1 | POST | `/doctor/call/{registerId}` | his |
| 病历编辑 | P1 | GET/PUT | `/doctor/medical-records/{registerId}` | his |
| 开立检查 | P3 | POST | `/doctor/check-requests` 或 `/pacs/requests` | his → pacs |
| 开立检验 | P2 | POST | `/doctor/inspection-requests` 或 **`/lis/requests`** | his → lis |
| 开立处置 | P3 | POST | `/doctor/disposal-requests` | his |
| 开立处方 | P3 | POST | `/doctor/prescriptions` | his |
| 查看医技结果 | P2+ | GET | `/doctor/registers/{id}/results` | his |
| AI 助理（7:3 右侧） | P4 | SSE | `/ai/assistant/stream` | ai-bridge |

### 2.3 挂号收费员 `REGISTRAR`

| 页面 | 阶段 | Method | API | 服务 |
|------|------|--------|-----|------|
| 窗口挂号 | P2 | POST | `/registrar/registers` | his |
| 收费 | P2 | POST | `/registrar/charges` | his |
| 退费 | P2 | POST | `/registrar/refunds` | his |
| 患者费用查询 | P2 | GET | `/registrar/patients/{id}/bills` | his |

### 2.4 检验科 `LAB_DOCTOR`

| 页面 | 阶段 | Method | API | 服务 |
|------|------|--------|-----|------|
| 待执行队列 | P2 | GET | **`/lis/queue`** | lis |
| 录入结果 | P2 | PUT | **`/lis/requests/{id}/result`** | lis |

### 2.5 检查科 `CHECK_DOCTOR`

| 页面 | 阶段 | Method | API | 服务 |
|------|------|--------|-----|------|
| 待执行队列 | P3 | GET | **`/pacs/queue`** | pacs |
| 录入结果 | P3 | PUT | **`/pacs/requests/{id}/result`** | pacs |
| 影像任务列表 | P3 | GET | **`/pacs/imaging-studies`** | pacs |

### 2.6 药师 `PHARMACIST`

| 页面 | 阶段 | Method | API | 服务 |
|------|------|--------|-----|------|
| 待发药列表 | P3 | GET | `/pharmacy/pending` | his |
| 发药 | P3 | POST | `/pharmacy/dispense/{prescriptionId}` | his |

### 2.7 管理员 `ADMIN`

| 页面 | 阶段 | Method | API | 服务 |
|------|------|--------|-----|------|
| 科室/员工/号别 | P1 | CRUD | `/admin/departments` 等 | management |
| 排班维护 | P1/P5 | CRUD | `/admin/scheduling` | management |
| 药品/医技字典 | P2/P3 | CRUD | `/admin/drugs`, `/admin/medical-technologies` | management |

---

## 三、路径演进说明（ADR-004）

| 旧路径（兼容期） | 新路径（P2 起优先） | 服务 |
|------------------|---------------------|------|
| `/doctor/inspection-requests` | `/lis/**` | lis |
| `/doctor/check-requests` | `/pacs/**` | pacs |
| `/patient/imaging/upload` | `/pacs/imaging/upload` | pacs |

Gateway 可双路由；**新页面请用新路径**。

---

## 四、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05 | 小程序 + PC 角色菜单与 API 对照 |
