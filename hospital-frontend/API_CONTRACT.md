# hospital-frontend 联调 API 契约（与 FRONTEND_API_MAP 对齐）

> Base: `http://localhost:9000/api/v1`  
> 临床检查/检验/处置 **AI 草稿暂不落库**，仅走 API 三步（POST/PUT/confirm）。  
> 医技结果仅 **`resultText` + `resultAttachment`**，无 `result-detail` / `ai-report`。

## 公共

| Method | Path |
|--------|------|
| POST | `/auth/staff/login` |

## 门诊医生 `/doctor`

| Method | Path |
|--------|------|
| GET | `/doctor/queues` |
| POST | `/doctor/registers/{registerId}/call` |
| POST | `/doctor/registers/{registerId}/finish` |
| GET | `/doctor/registers/{registerId}/medical-record` |
| PUT | `/doctor/registers/{registerId}/medical-record` |
| POST | `/doctor/registers/{registerId}/medical-record/confirm` |
| GET | `/doctor/registers/{registerId}/orders` |
| GET | `/doctor/registers/{registerId}/results` |
| GET | `/doctor/diseases` |
| POST | `/doctor/check-requests` 等手工开单 |
| POST/PUT/confirm | `/doctor/*-requests/ai-draft/**` |
| POST | `/ai/diagnosis/suggest` |

病历 Request 疾病：`diseaseEntries: [{ diseaseId, diseaseType }]`（1 主要 / 2 次要）。

## 职员自助 `/staff`

| Method | Path |
|--------|------|
| GET | `/staff/my-schedules` |
| POST | `/staff/schedules/{schedulingId}/leave-requests` |
| POST | `/staff/leave-requests/{id}/cancel` |

## 管理员 `/admin`

| Method | Path |
|--------|------|
| GET/POST/PUT/DELETE | `/admin/departments` |
| GET/POST/PUT/DELETE | `/admin/employees`（建档即含 `username` 登录） |
| GET | `/admin/schedules` |
| POST | `/admin/schedules` |
| PUT | `/admin/schedules/{id}` |
| POST | `/admin/schedules/{id}/publish` |
| POST | `/admin/schedules/ai-suggest` |
| GET | `/admin/leave-requests` |
| POST | `/admin/leave-requests/{id}/approve` |
| POST | `/admin/leave-requests/{id}/reject` |

替班/AI 建议：**仅 `PUT /admin/schedules/{id}`** 更新 `employeeId` 等，无单独 `ai-replace`。

## 挂号收费 `/registrar`

| Method | Path |
|--------|------|
| POST | `/registrar/registers` |
| POST | `/registrar/charges` |
| POST | `/registrar/refunds` |
| GET | `/registrar/patients/{medicalRecordNo}/bills` |

## 医技

| 服务 | 队列 | 录入结果 |
|------|------|----------|
| LIS | GET `/lis/queue` | PUT `/lis/requests/{id}/result` |
| PACS | GET `/pacs/queue` | PUT `/pacs/requests/{id}/result` |
| 处置 | GET `/disposal/queue` | PUT `/disposal/requests/{id}/result` |

## 药房

| Method | Path |
|--------|------|
| GET | `/pharmacy/pending` |
| POST | `/pharmacy/prescriptions/{id}/dispense` |
