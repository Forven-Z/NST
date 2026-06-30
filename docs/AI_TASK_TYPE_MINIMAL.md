# 三种 CNN 任务 — 最小改动说明（第二步）

> **版本**：v1.0 | 2026-06  
> **负责人**：wsh（hospital-ai）+ lzr/zcl（pacs / 契约，按需）  
> **前提**：第一步头部 CT 正式链路（Gateway → pacs → hospital-ai → MinIO）已能基本跑通。  
> **目标**：区分头部 / 肺部 / 肿瘤三种 AI 任务，**不新建数据库表**，**不训练新模型**（肺部、肿瘤可先明确失败提示）。

---

## 一、改动范围总览

| 类别 | 做不做 | 说明 |
|------|--------|------|
| 新建 PostgreSQL 表 | ❌ 不做 | 单库 `hospital`，表结构已够用 |
| `ALTER TABLE` 加列 | ❌ 不做 | 用已有 `imaging_study.modality`、`report_json` |
| `seed-dict.sql` 加检查项目 | ✅ 做 | 字典里补「肺部 CT」等 |
| Java `ImagingService` 推断类型 | ✅ 做 | 写入 `modality`，调 AI 时传 `taskType` |
| Java `HospitalAiClient` | ✅ 做 | 请求体增加 `taskType` |
| Python `hospital-ai` | ✅ 做小改 | 接收 `taskType`；头部照旧；其余 STUB 失败 |
| 前端 `ImagingAiView` | ✅ 做小改 | 标题随 `itemName` 变化（可选本步） |
| 训练肺部 / 肿瘤模型 | ❌ 本步不做 | 放在第三、四步 |

---

## 二、相关表现状（你现在有什么）

### 2.1 数据库（PostgreSQL · 库名 `hospital`）

与 CNN 直接相关的 **4 张表**：

| 表 | 作用 | 和任务类型的关系 |
|----|------|------------------|
| `medical_technology` | 检查项目字典（「头部 CT」） | 医生开单时选哪一项 |
| `check_request` | 检查申请单 | 通过 `medical_technology_id`、`body_part` 关联项目 |
| `imaging_study` | 一次影像 AI 任务 | **`modality` 存任务类型**；`report_json` 存 AI 结果 |
| `patient` | 患者 | 联查用，本步不改 |

`imaging_study` 关键字段（`docs/sql/schema.sql` 已有，无需改表）：

```sql
modality            VARCHAR(16),   -- 设计值：CT_HEAD / CT_LUNG / TUMOR_SEG
report_json         JSONB,         -- 可含 taskType、maskVoxelCount、aiReportText 等
status              VARCHAR(16),   -- PENDING / PROCESSING / COMPLETED / FAILED
source_object_key   VARCHAR(512),  -- MinIO 原 CT
result_object_key   VARCHAR(512)   -- MinIO 掩码（逻辑路径在 report_json 里更全）
```

### 2.2 当前代码问题

`ImagingService.inferModality()` 上传时几乎只写入 `"CT"`，未区分头部 / 肺部 / 肿瘤：

```java
// 现状（需替换）
if (itemName.contains("CT")) {
    return "CT";
}
```

`HospitalAiClient.submitInferenceJob()` **未传** `taskType`。  
Python `jobs.py` **写死**头部 `CTArtifactInfer`。

### 2.3 MinIO（不是数据库）

- bucket：`imaging`
- 路径：`studies/{checkRequestId}/`（源数据与结果掩码）
- 本步 **不改路径规则**

---

## 三、约定：modality 与 taskType 对照

| 检查项目示例（`item_name`） | `imaging_study.modality` | 调 Python 的 `taskType` | 本步模型 |
|----------------------------|--------------------------|-------------------------|----------|
| 头部 CT | `CT_HEAD` | `HEAD_CT_ARTIFACT` | ✅ 已有 `best.pth` |
| 肺部 CT | `CT_LUNG` | `LUNG_CT_ARTIFACT` | ⬜ STUB：明确失败 |
| 肿瘤 CT / 肿瘤分割（名称组内可定） | `TUMOR_SEG` | `TUMOR_SEG` | ⬜ STUB：明确失败 |
| 其他含 CT 的项目 | `CT_HEAD` | `HEAD_CT_ARTIFACT` | 默认兼容现有 demo |

推断规则（Java 内实现，**前后端统一**）：

1. `itemName` 或 `bodyPart` 含 **胸、肺** → `CT_LUNG`
2. 含 **肿瘤、病灶、肿物** → `TUMOR_SEG`
3. 含 **头、颅、脑**，或现有「头部 CT」→ `CT_HEAD`
4. 仅含 CT 且无法判断 → `CT_HEAD`（与 demo 一致）

---

## 四、改动清单（按执行顺序）

### 4.1 数据：补充检查项目字典

**文件**：`docs/sql/seed-dict.sql`

在 `medical_technology` 的 `INSERT` 中**追加行**（`ON CONFLICT DO NOTHING` 保持不变）：

```sql
INSERT INTO medical_technology (item_code, item_name, tech_type, price, dept_id) VALUES
    ('CHK-CT-HEAD', '头部 CT', 'CHECK', 280.00, 2),
    ('CHK-CT-LUNG', '肺部 CT', 'CHECK', 320.00, 2),
    ('CHK-TUMOR-SEG', '肿瘤 CT 分割', 'CHECK', 450.00, 2),
    ('INS-BLOOD', '血常规', 'INSPECTION', 35.00, 3),
    ('DIS-WASH', '洗胃', 'DISPOSAL', 120.00, 1)
ON CONFLICT (item_code) DO NOTHING;
```

**执行**（已有机库时只补字典，不必重建全库）：

```powershell
psql -U postgres -d hospital -f docs/sql/seed-dict.sql
```

**可选**：`docs/sql/seed-demo-check.sql` 再插一条 `check_request id=62002` 的「肺部 CT」演示单，便于自测。

---

### 4.2 Java：推断 modality 并写入 `imaging_study`

**文件**：`hospital-backend/hospital-pacs/.../service/ImagingService.java`

**改动 A**：重写 `inferModality(Map<String, Object> check)`，返回 `CT_HEAD` / `CT_LUNG` / `TUMOR_SEG`（不再返回笼统 `"CT"`）。

建议实现要点：

- 读取 `check.get("itemName")`、`check.get("bodyPart")`、`check.get("purpose")`，拼成一个大写字符串再 `contains` 判断。
- 上传影像 `uploadImaging()` 里已调用 `inferModality(check)` 并 `insertPending(..., modality, ...)`，**改方法即可**，Repository 不用动。

**改动 B**：新增私有方法 `resolveTaskType(String modality)`：

```text
CT_HEAD   → HEAD_CT_ARTIFACT
CT_LUNG   → LUNG_CT_ARTIFACT
TUMOR_SEG → TUMOR_SEG
其他       → HEAD_CT_ARTIFACT
```

**改动 C**：`generateAiReport()` 提交 job 时：

```java
String modality = String.valueOf(study.get("modality"));
String taskType = resolveTaskType(modality);
hospitalAiClient.submitInferenceJob(
    studyId, checkRequestId,
    sourceBucket, sourceObjectKey,
    resultPrefix,
    taskType   // 新增参数
);
```

若 `study` 里 `modality` 仍是旧的 `"CT"`，可在 `generateAiReport` 开头用 `check` 再算一次并 `UPDATE imaging_study.modality`（兼容历史数据）。

**改动 D**（可选）：`buildResultDetail()` 增加返回字段：

```java
result.put("modality", study != null ? study.get("modality") : null);
result.put("taskType", resolveTaskType(...));
```

方便前端显示，**非必须**。

---

### 4.3 Java：调用 Python 时带上 taskType

**文件**：`hospital-backend/hospital-pacs/.../client/HospitalAiClient.java`

方法签名增加最后一参 `String taskType`，body 增加：

```java
body.put("taskType", taskType);
```

---

### 4.4 Python：接收 taskType，头部照旧，其余 STUB

**文件**（最小集）：

| 文件 | 改动 |
|------|------|
| `hospital-ai/app/main.py` | `CreateJobRequest` 增加 `taskType: str = "HEAD_CT_ARTIFACT"`；传入 `submit_job` |
| `hospital-ai/app/jobs.py` | `InferenceJob` 增加字段 `task_type`；`run_inference_job` 开头判断：<br>• `HEAD_CT_ARTIFACT` → 现有逻辑<br>• `LUNG_CT_ARTIFACT` / `TUMOR_SEG` → 抛 `RuntimeError("xxx 模型尚未部署")` |
| `hospital-ai/app/jobs.py` | `report_json` 里增加 `"taskType": job.task_type` |

**本步可不建** `router.py`，用 `if/elif` 即可；模型多了再抽。

---

### 4.5 前端：标题随检查项目变化（可选，半页工作量）

**文件**：`hospital-frontend/src/views/pacs/ImagingAiView.vue`

增加 `computed`：

```javascript
const pageTitle = computed(() => {
  const name = itemName.value || ''
  if (/胸|肺/.test(name)) return '肺部 CT 伪影检测'
  if (/肿瘤|病灶|肿物/.test(name)) return '肿瘤分割分析'
  return '头部 CT 金属伪影检测'
})
```

模板：`<h1>{{ pageTitle }}</h1>`，替换写死的「CT 金属伪影检测」。

失败时 `aiReportText` / `errorMessage` 已能展示 Python STUB 文案，一般不用改 API。

---

### 4.6 文档（建议同步，可与 zcl 分工）

| 文件 | 内容 |
|------|------|
| `docs/API.md` | `POST /v1/inference/jobs` 请求体增加 `taskType` |
| `docs/AI_CNN_INTEGRATION.md` | 增加 §「三任务 modality / taskType」 |
| `docs/PROGRESS.md` | hospital-ai 行：任务分发已通，②③ 模型待训 |

**本最小改动不要求**改 `schema.sql`（表结构不变）。

---

## 五、数据流（改完后）

```text
1. 医生开「肺部 CT」
   → check_request + medical_technology（库中已有字典行）

2. 检查医生上传 CT
   → pacs inferModality → imaging_study.modality = 'CT_LUNG'

3. 点 AI 分析
   → pacs resolveTaskType → taskType = 'LUNG_CT_ARTIFACT'
   → POST hospital-ai /v1/inference/jobs { taskType, studyId, ... }

4. Python
   → LUNG：STUB 失败，回调 FAILED + 明确 errorMessage

5. 前端
   → 标题「肺部 CT 伪影检测」
   → 侧栏显示失败原因（模型尚未部署）

头部 CT 走 HEAD_CT_ARTIFACT，行为与第一步一致。
```

---

## 六、自测清单（本步 DoD）

| # | 操作 | 预期 |
|---|------|------|
| 1 | 执行 `seed-dict.sql` 后查字典 | `SELECT item_code, item_name FROM medical_technology WHERE tech_type='CHECK';` 含 HEAD/LUNG/TUMOR 三项 |
| 2 | 演示单 62001 头部 CT 全流程 | 与第一步相同，掩码 + 报告成功 |
| 3 | 查 `imaging_study` | `modality = 'CT_HEAD'`（不再是 `'CT'`） |
| 4 | 新建或模拟「肺部 CT」检查单并触发 AI | `modality = 'CT_LUNG'`，分析失败且提示含「肺部」或「未部署」 |
| 5 | `report_json`（头部成功时） | 含 `taskType: "HEAD_CT_ARTIFACT"` |
| 6 | 回归登录、上传、预览 URL | 无破坏 |

**SQL 抽查示例**：

```sql
SELECT id, check_request_id, modality, status, report_json
FROM imaging_study
ORDER BY id DESC
LIMIT 5;
```

---

## 七、文件改动一览表

| 序号 | 文件路径 | 操作 |
|------|----------|------|
| 1 | `docs/sql/seed-dict.sql` | 加 2 条检查项目 |
| 2 | `docs/sql/seed-demo-check.sql` | 可选：肺部 CT 演示单 |
| 3 | `hospital-pacs/.../ImagingService.java` | 改 `inferModality`；加 `resolveTaskType`；`generateAiReport` 传 taskType |
| 4 | `hospital-pacs/.../HospitalAiClient.java` | body 加 `taskType` |
| 5 | `hospital-ai/app/main.py` | 请求模型加 `taskType` |
| 6 | `hospital-ai/app/jobs.py` | 按 taskType 分支；report_json 带 taskType |
| 7 | `hospital-frontend/.../ImagingAiView.vue` | 可选：动态 `pageTitle` |
| 8 | `docs/API.md` 等 | 契约同步 |

**预计不涉及**：`ImagingStudyRepository.java`（除非要补 `UPDATE modality` 方法）、`schema.sql`、MinIO 配置、Gateway 路由。

---

## 八、本步明确不做的事

- 不训练肺部 / 肿瘤模型，不新增 `.pth` 文件要求  
- 不拆三个 `hospital-ai` 进程  
- 不做三个独立前端页面（仍从检查队列进同一工作台）  
- 不让检查医生在 AI 页手动下拉选任务类型（由检查项目自动推断）

---

## 九、下一步（第三步预告）

| 任务 | 工作 |
|------|------|
| 肺部 CT 伪影 | 数据集 + 训练 + `lung_artifact_best.pth` + 去掉 LUNG STUB |
| 肿瘤分割 | 定范围 + 标注 + 训练 + 报告加体积统计 + 去掉 TUMOR STUB |

第三步起再在 `jobs.py` 抽 `inference/router.py`、在 `config.py` 配置多权重路径。

---

## 十、附录：推断逻辑伪代码（Java）

```java
private String inferModality(Map<String, Object> check) {
    String blob = joinUpper(
        check.get("itemName"),
        check.get("bodyPart"),
        check.get("purpose")
    );
    if (containsAny(blob, "胸", "肺", "CHEST", "LUNG")) {
        return "CT_LUNG";
    }
    if (containsAny(blob, "肿瘤", "病灶", "肿物", "TUMOR")) {
        return "TUMOR_SEG";
    }
    if (containsAny(blob, "头", "颅", "脑", "HEAD")) {
        return "CT_HEAD";
    }
    if (blob.contains("CT")) {
        return "CT_HEAD"; // 默认与现有 demo 一致
    }
    return "CT_HEAD";
}

private String resolveTaskType(String modality) {
    return switch (modality) {
        case "CT_LUNG" -> "LUNG_CT_ARTIFACT";
        case "TUMOR_SEG" -> "TUMOR_SEG";
        default -> "HEAD_CT_ARTIFACT";
    };
}
```

---

*实施前建议组内 Ack 本节「三、约定」表格；实施后以「六、自测清单」为准收工。*
