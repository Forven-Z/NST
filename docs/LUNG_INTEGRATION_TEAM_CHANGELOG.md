# 肺部 CT 模型集成 — 小组变更说明（wsh）

> **分支**：`feature/ai-task-type`  
> **日期**：2026-06-15  
> **原则**：与头部 CT 共用同一套界面与 API；仅按 `taskType` 切换权重，**不改动**其他成员已稳定的头部链路。

---

## 一、集成结果（本次完成后）

| 项 | 状态 |
|----|------|
| 训练权重 | `lung_artifact_best.pth`（验证集最优 Dice **0.8657**，100 epoch） |
| 部署路径 | `hospital-ai/model/weights/lung_artifact_best.pth`（**不进 Git**，与 `best.pth` 相同） |
| 任务类型 | `LUNG_CT_ARTIFACT` |
| 演示检查单 | 队列 **#62002**「胸部 CT」 |
| 头部回归 | **#62001**「头部 CT」仍用 `best.pth`，行为不变 |

---

## 二、对组员的影响（结论）

| 角色 | 是否需要改代码 | 说明 |
|------|----------------|------|
| 前端（lzr 等） | **否** | 仍用 `ImagingAiView.vue` + `MprViewer.vue`；标题按检查项目自动切换 |
| Java / pacs（lzr+wsh） | **否**（此前已合入） | `ImagingService` 已按「胸部 CT」→ `LUNG_CT_ARTIFACT` |
| Python / hospital-ai（wsh） | **仅 1 行 import 修复** | 见下文「本次新增修改」 |
| 其他后端服务 | **否** | auth / gateway / his / management 无改动 |

**组员本地若要跑肺部推理**：自行拷贝权重文件（见第四节），重启 `start-r-pacs-ai.bat`，无需合并冲突级别的重构。

---

## 三、架构（与头部一致）

```text
技师队列「胸部 CT」#62002
  → 影像 AI 工作台（/pacs/imaging-ai?itemName=胸部 CT）
  → POST /pacs/imaging/upload
  → POST /pacs/requests/{id}/ai-report
       └─ pacs: itemName 含「胸/肺」→ modality=CT_LUNG → taskType=LUNG_CT_ARTIFACT
  → hospital-ai: get_infer(LUNG_CT_ARTIFACT) 加载 lung_artifact_best.pth
  → MinIO 掩码 + 预览 → 回调 pacs → 前端 MPR 叠加掩码
```

头部 **#62001** 路径相同，仅 `taskType=HEAD_CT_ARTIFACT` + `best.pth`。

---

## 四、组员如何拿到权重（已改为随 Git 分发）

权重放在 **`shared/model-weights/`**（随仓库提交，约 33MB × 2）。  
`hospital-ai/model/weights/` 仍为运行目录，在 `.gitignore` 中。

**拉代码后执行一次：**

```powershell
cd NST-work
powershell -ExecutionPolicy Bypass -File scripts\install-model-weights.ps1
```

脚本会把 `best.pth` 与 `lung_artifact_best.pth` 复制到 `hospital-ai/model/weights/`。

组长更新模型：替换 `shared/model-weights/*.pth` 后 commit，组员 `git pull` 再跑安装脚本。

---

## 五、代码变更清单（按模块）

### 5.1 此前已在分支上（头部集成时一并铺好，肺部复用）

| 文件 | 变更要点 |
|------|----------|
| `hospital-ai/app/config.py` | 新增 `lung_model_weight_path` |
| `hospital-ai/app/jobs.py` | 双单例 `_infer_head` / `_infer_lung`；无肺部权重时 STUB，**不用头部权重冒充** |
| `hospital-ai/app/inference/task_types.py` | `LUNG_CT_ARTIFACT` 常量 |
| `hospital-ai/app/inference/reports.py` | 肺部专用 AI 报告文案 |
| `hospital-pacs/.../ImagingService.java` | `inferModality` + `resolveTaskType` |
| `hospital-pacs/.../HospitalAiClient.java` | 请求体带 `taskType` |
| `hospital-frontend/.../ImagingAiView.vue` | `itemName` 含胸/肺 → 标题「肺部 CT 伪影检测」 |
| `hospital-frontend/.../MprViewer.vue` | 与头部共用，无 taskType 分支 |
| `docs/sql/seed-dict.sql` | `CHK-CT-LUNG` |
| `docs/sql/seed-demo-check.sql` | 演示单 **#62002** 胸部 CT |

### 5.2 本次新增 / 修复（2026-06-15）

| 文件 | 变更 |
|------|------|
| `hospital-ai/model/weights/lung_artifact_best.pth` | **本地拷贝部署**（`.gitignore` 忽略，不提交） |
| `hospital-ai/.env` | 补充 `LUNG_MODEL_WEIGHT_PATH=...` |
| `hospital-ai/app/main.py` | **修复** `import LUNG_CT_ARTIFACT`（否则权重就位后预热 `NameError`） |
| `docs/LUNG_MODEL_INTEGRATION.md` | 更新为「权重已部署」状态 |
| `docs/LUNG_INTEGRATION_TEAM_CHANGELOG.md` | 本说明 |

### 5.3 未改动的文件（刻意保持）

- `hospital-frontend` 路由、API 封装（与头部相同）
- `best.pth` 及头部训练相关脚本
- auth / gateway / his / management 各模块
- 肿瘤 `TUMOR_SEG`（仍为 STUB）

---

## 六、验收步骤

1. `VITE_USE_MOCK=false`，执行 `seed-demo-check.sql`（含 62001 / 62002）
2. 技师登录 → 队列 **#62002 胸部 CT** → 影像 AI 工作台
3. 上传**胸部** DICOM 序列 → **开始 AI 检测** → 应成功并显示肺部报告文案
4. 回归 **#62001 头部 CT** → 行为与集成前一致
5. `GET /v1/health`：`modelLoaded` 与 `lungModelLoaded` 均为 `true`

---

## 七、训练侧（不在 NST-work 仓库内）

| 项 | 路径 |
|----|------|
| 训练工程 | `6.3/BrainCT-Lung` |
| 权重产出 | `run/weights/lung_artifact_best.pth` |
| 实验记录 | `6.3/BrainCT-Lung/肺部实验说明.md` |
| 曲线图 | `6.3/BrainCT-Lung/run/*.png` |

---

## 八、已知限制

- 前端 Mock 队列默认只有头部演示单；真实联调需关 Mock + 种子 SQL
- `TUMOR_SEG` 尚未实现
- 权重文件通过 **`shared/model-weights/`** 随 Git 分发；组员运行 `scripts/install-model-weights.ps1`
