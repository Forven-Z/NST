# 影像数据访问说明（跨模块 · MinIO + PostgreSQL）

> **读者**：his / 患者端 / 管理端等**非 pacs 模块**开发同学  
> **维护**：wsh（CNN / 影像 AI）  
> **版本**：v1.0 | 2026-06  
> **关联**：[AI_CNN_INTEGRATION.md](./AI_CNN_INTEGRATION.md)、[DATABASE_DESIGN.md](./DATABASE_DESIGN.md) §9.1、[MICROSERVICES.md](./MICROSERVICES.md) §2.5

---

## 一、先记住三件事

1. **MinIO 路径里只有「检查申请 ID」**，没有患者姓名、`patient_id`。
2. **患者是谁，查 PostgreSQL**（`imaging_study` / `check_request` / `patient` 三表关联）。
3. **别的模块不要直连 MinIO**，统一走 **Gateway → hospital-pacs API**（鉴权、科室隔离由 pacs 负责）。

---

## 二、ID 与存储对应关系

```text
patient（患者）
  id = patient_id          例：3
  medical_record_no        例：MR202606040003
  real_name                例：赵大爷
       │
       ▼
register（一次挂号）
  id = register_id
       │
       ▼
check_request（一张检查申请单）
  id = check_request_id    例：62001 或 1、2
  patient_id → patient.id
  result_text              医师确认后的最终文字报告（his / 患者端优先读这个）
       │
       ▼
imaging_study（一条影像 AI 任务）
  check_request_id
  patient_id               冗余一份，方便按患者查
  register_id
  source_object_key        MinIO 源数据前缀，例：studies/62001/source/
  result_object_key        MinIO 掩码，例：studies/62001/mask.nii.gz
  report_json              AI 结构化结果（含预览路径、aiReportText）
```

### MinIO 目录（bucket：`imaging`）

```text
imaging/
└── studies/
    └── {check_request_id}/          ← 注意：文件夹名 = 检查申请 ID，不是 patient_id
        ├── source/                  ← 原始 DICOM / NIfTI（pacs 上传）
        ├── ct_preview.nii.gz        ← 网页预览 CT（hospital-ai 写入）
        └── mask.nii.gz              ← CNN 掩码（hospital-ai 写入）
```

**常见误解**：在 MinIO 控制台看到 `studies/1/`，不代表「1 号患者」，而是 **检查申请 #1**。要知道对应哪位患者，必须查库或调 pacs 列表 API。

---

## 三、按 patient_id 查影像（推荐流程）

### 步骤 1：用 patient_id 列出该患者所有影像任务

```http
GET /api/v1/pacs/imaging-studies?patientId={patientId}
Authorization: Bearer {token}
```

也可用病历号：

```http
GET /api/v1/pacs/imaging-studies?medicalRecordNo=MR202606040003
```

**响应 `data.list[]` 字段**：

| 字段 | 说明 |
|------|------|
| `studyId` | `imaging_study.id` |
| `checkRequestId` | **后续所有接口都用这个 ID** |
| `patientId` | 患者 ID |
| `patientName` | 患者姓名 |
| `medicalRecordNo` | 病历号 |
| `itemName` | 检查项目名 |
| `status` | `PENDING` / `IN_PROGRESS` / `COMPLETED` |
| `resultReady` | `true` 表示可取 AI 结果 |

### 步骤 2：用 checkRequestId 取报告详情

```http
GET /api/v1/pacs/requests/{checkRequestId}/result-detail
```

关注字段：

- `aiReportText` — AI 生成的文字报告
- `aiReportStatus` — `READY` / `PENDING` / `FAILED`
- `doctorReportText` — 医师已确认部分（若有）
- `resultText` — 检查单上的完整结果文本

### 步骤 3：需要 NIfTI 文件时（预览 / 掩码）

```http
GET /api/v1/pacs/imaging/preview/{checkRequestId}/ct
GET /api/v1/pacs/imaging/preview/{checkRequestId}/mask
```

返回 `application/gzip` 流（`.nii.gz`），**不要**自行拼接 MinIO URL。

### 步骤 4：医师确认后的最终结果（给 his / 患者端）

优先读 **`check_request.result_text`**（与 lis / disposal 医技结果一致）：

```sql
SELECT cr.id, cr.result_text, cr.result_time, cr.status
FROM check_request cr
WHERE cr.patient_id = :patientId
  AND cr.delmark = 0
ORDER BY cr.result_time DESC NULLS LAST;
```

---

## 四、SQL 示例（后端 / 数据分析）

### 4.1 按 patient_id 查全部影像任务

```sql
SELECT
  ist.id              AS study_id,
  ist.study_no,
  cr.id               AS check_request_id,
  p.id                AS patient_id,
  p.real_name         AS patient_name,
  p.medical_record_no,
  mt.item_name,
  ist.status          AS study_status,
  ist.source_object_key,
  ist.result_object_key,
  ist.report_json->>'aiReportText' AS ai_report_text,
  ist.report_json->>'previewObjectKey' AS preview_key,
  ist.report_json->>'maskObjectKey' AS mask_key
FROM imaging_study ist
JOIN check_request cr ON cr.id = ist.check_request_id
JOIN patient p ON p.id = cr.patient_id
JOIN medical_technology mt ON mt.id = cr.medical_technology_id
WHERE p.id = :patientId
  AND cr.delmark = 0
ORDER BY ist.create_time DESC;
```

### 4.2 按病历号查（演示患者赵大爷）

```sql
SELECT ist.check_request_id, p.real_name, ist.status, ist.source_object_key
FROM patient p
JOIN check_request cr ON cr.patient_id = p.id
LEFT JOIN imaging_study ist ON ist.check_request_id = cr.id
WHERE p.medical_record_no = 'MR202606040003'
  AND cr.delmark = 0;
```

演示数据检查申请 ID 为 **62001**（见 `docs/sql/seed-demo-check.sql`），若已跑过 AI，MinIO 路径为 `studies/62001/`。

### 4.3 只有 check_request_id，反查患者

```sql
SELECT cr.id AS check_request_id, p.id AS patient_id, p.real_name, p.medical_record_no
FROM check_request cr
JOIN patient p ON p.id = cr.patient_id
WHERE cr.id = :checkRequestId;
```

---

## 五、report_json 字段契约

`imaging_study.report_json`（AI 完成后由 pacs 写入）：

```json
{
  "maskVoxelCount": 12345,
  "maskSliceIndices": [10, 11, 12],
  "sliceCount": 256,
  "spacing": [0.5, 0.5, 1.0],
  "modality": "CT",
  "aiReportText": "AI 影像分析完成：…",
  "previewObjectKey": "studies/62001/ct_preview.nii.gz",
  "maskObjectKey": "studies/62001/mask.nii.gz"
}
```

跨模块**优先读 API 返回的 `aiReportText`**；仅在运维/排错时直接解析 `report_json` 或 `*_object_key`。

---

## 六、各模块怎么接

| 模块 | 典型场景 | 做法 |
|------|----------|------|
| **hospital-his** | 门诊医生看某次就诊的检查 AI 报告 | Feign/HTTP 调 pacs `result-detail`；或 JOIN `check_request` + `imaging_study` |
| **患者小程序** | 患者看本人检查报告 | JWT 取 `patient_id` → `imaging-studies?patientId=` → `result-detail`；**禁止**越权传他人 ID |
| **hospital-management** | 任务监控 | `imaging-studies` 列表 + `imaging_study.status` 统计 |
| **hospital-lis / disposal** | 一般不读 CT 文件 | 仅需文字结果时读 `check_request.result_text` |
| **hospital-ai** | CNN 推理 | 仅内网；源路径由 pacs 下发 `source.objectKeyPrefix`，不面向业务模块 |

---

## 七、禁止事项

| 不要做 | 原因 |
|--------|------|
| 用 MinIO 文件夹名 `1`、`2` 当患者 ID | 那是 `check_request_id` |
| 各服务各自配 MinIO AK/SK 扫 bucket | 破坏微服务边界，无法做权限隔离 |
| 把 MinIO 对象 URL 暴露给患者浏览器 | 应经 pacs 鉴权后代理下载 |
| 在业务库再建一张「MinIO 文件表」 | 与 `imaging_study` 重复；路径已在 `source_object_key` / `report_json` |

---

## 八、联调示例（curl）

```bash
# 1. 登录拿 token（检查医生 check01 / 123456）
TOKEN=$(curl -s -X POST http://127.0.0.1:9000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"check01","password":"123456"}' | jq -r .data.token)

# 2. 按 patient_id 查影像任务（将 3 换成真实 patient_id）
curl -s "http://127.0.0.1:9000/api/v1/pacs/imaging-studies?patientId=3" \
  -H "Authorization: Bearer $TOKEN" | jq .

# 3. 取某次检查的报告（将 62001 换成上一步的 checkRequestId）
curl -s "http://127.0.0.1:9000/api/v1/pacs/requests/62001/result-detail" \
  -H "Authorization: Bearer $TOKEN" | jq .data.aiReportText
```

---

## 九、FAQ

**Q：MinIO 里 studies/1 和 studies/62001 有什么区别？**  
A：都是 `check_request.id`。`1`、`2` 是早期联调自增 ID；`62001` 是演示脚本 `seed-demo-check.sql` 插入的固定 ID。规则相同，只是数字不同。

**Q：患者做过多次 CT 怎么办？**  
A：每次开立检查会有一条 `check_request` 和（上传后）一条 `imaging_study`。用 `patientId` 查列表会返回多条，按 `checkRequestId` 区分。

**Q：AI 还没跑完能查到吗？**  
A：能查到任务，`status` 为 `PENDING` / `PROCESSING`，`resultReady=false`，尚无 `mask.nii.gz`。

**Q：his 只要最终报告，要不要调影像 API？**  
A：一般不需要。读 `check_request.result_text` 即可（检查医生在队列确认录入后写入）。

---

## 十、变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06 | 初版：MinIO 路径规范、patient_id 查询流程、`imaging-studies?patientId=` |
