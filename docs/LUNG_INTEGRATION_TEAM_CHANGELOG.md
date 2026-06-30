# 头部 + 肺部 CT CNN 集成 — 小组说明（wsh）

> **分支**：`feature/ai-task-type`（push 至 `origin`，待合并 `main`）  
> **最新提交**：`feature/ai-task-type` — 含 PACS 队列 + 影像工作台（§6.6）
> **负责人**：wsh（CNN / hospital-ai + PACS 检查队列前端）  
> **原则**：头部与肺部共用同一界面与 API；按检查项目自动选模型；**CNN 只出掩码，文字 AI 报告由大模型组负责**

---

## 一、你需要做什么（按角色）

| 角色 | 是否要改代码 | 你要做的 |
|------|-------------|----------|
| **所有人** | 否 | `git pull` 拉分支 `feature/ai-task-type`（或等 PR 合并 main 后 pull main） |
| **要跑 CNN 的同学** | 否 | 执行 `scripts/install-model-weights.ps1`，再 `start-r-pacs-ai.bat` |
| **前端（PACS 队列）** | 否 | 已改 `TechQueuePanel` / `ImagingAiView`，pull 后 `npm run dev` 即可 |
| **前端** | 否 | 已设 `VITE_USE_MOCK=false`；联调前跑 `seed-demo-check.sql`（队列空时再跑 `seed-demo-check-extra.sql`） |
| **大模型组** | 在各自模块 | 文字报告走队列「录入结果」等界面，**不要**依赖 CNN 工作台里的报告栏（已移除） |
| **his / 患者端等** | 否 | 读 pacs API，见 [IMAGING_DATA_ACCESS.md](./IMAGING_DATA_ACCESS.md) |
| **组长 wsh** | — | 在 GitHub 开 PR：`feature/ai-task-type` → `main` |

**PR 链接**：https://github.com/Forven-Z/NST/pull/new/feature/ai-task-type

---

## 二、集成了什么

| 项 | 头部 CT | 肺部 CT |
|----|---------|---------|
| 演示检查单 | **#62001** | **#62002** |
| taskType | `HEAD_CT_ARTIFACT` | `LUNG_CT_ARTIFACT` |
| 权重文件 | `best.pth` | `lung_artifact_best.pth`（Dice ≈ 0.87） |
| MinIO 路径 | `studies/62001/` | `studies/62002/` |
| 前端入口 | 同一页 `/pacs/imaging-ai` | 同上（标题：**肺部 CT 伪影检测**） |
| CNN 输出 | 掩码 + CT 预览 NIfTI | 同上 |
| CNN 文字报告 | **无**（已删除） | **无**（已删除） |

**分发规则**（`ImagingService.java`）：检查项目名含「胸/肺」→ 肺部模型；含「头/颅/脑」或默认 → 头部模型。

---

## 三、架构（不变）

```text
技师队列（仅 CHECK 类型）
  status=20「开始执行」→ execute API → 跳转影像 AI 工作台
  status=30「影像 AI 工作台」+「录入结果」（队列内）
  status=40「查看影像」→ 工作台查看模式（自动加载 MinIO 预览）
  工作台内（非查看模式）右上角另有「录入结果」，与队列弹窗相同
  → Gateway → pacs → hospital-ai :8000（CNN）
  → MinIO：source/ + mask.nii.gz + ct_preview.nii.gz
  → 前端 MprViewer 三视图
```

文字版 AI 报告：**不在本链路**，由大模型组在其他界面实现。

---

## 四、组员本地启动（完整顺序）

```powershell
cd NST-work

# 1. 拉代码
git fetch origin
git checkout feature/ai-task-type   # 或 merge 后的 main

# 2. 安装 CNN 权重（从 shared/ 复制到运行目录）
powershell -ExecutionPolicy Bypass -File scripts\install-model-weights.ps1

# 3. 基础设施：PostgreSQL + Nacos + MinIO（见 LOCAL_WORKSPACE.md）

# 4. 演示数据（只需一次）
powershell -ExecutionPolicy Bypass -File scripts\seed-demo-check.ps1

# 5. Java + hospital-ai
scripts\start-r-pacs-ai.bat

# 6. 验证 CNN
# 浏览器 http://127.0.0.1:8000/v1/health
# 期望 modelLoaded + lungModelLoaded 均为 true，device 含 cuda

# 7. 前端
cd hospital-frontend
npm run dev
# http://127.0.0.1:5173  check01 / 123456
```

---

## 五、权重与 Git

| 路径 | 是否进 Git | 说明 |
|------|-----------|------|
| `shared/model-weights/best.pth` | ✅ | 头部，约 33MB |
| `shared/model-weights/lung_artifact_best.pth` | ✅ | 肺部，约 33MB |
| `hospital-ai/model/weights/*.pth` | ❌ | 运行副本，`.gitignore` |

更新模型：替换 `shared/model-weights/` → commit → 组员 `git pull` 再跑安装脚本。

---

## 六、本次 Push 变更清单（相对 main）

### 6.1 hospital-ai（Python）

| 文件 | 变更 |
|------|------|
| `app/main.py` | 双模型预热；health 返回 `lungModelLoaded` |
| `app/jobs.py` | `_infer_head` / `_infer_lung`；无肺部权重时 STUB |
| `app/inference/task_types.py` | **新增** taskType 常量 |
| `app/config.py` | `lung_model_weight_path` |
| ~~`app/inference/reports.py`~~ | **已删除**（CNN 不再生成文字报告） |

### 6.2 hospital-pacs（Java）

| 文件 | 变更 |
|------|------|
| `ImagingService.java` | `inferModality` + `resolveTaskType`；回调不落 CNN 文字 |
| `HospitalAiClient.java` | 请求体带 `taskType` |
| `ImagingStudyRepository.java` | modality 字段支持 |

### 6.3 hospital-frontend

| 文件 | 变更 |
|------|------|
| `ImagingAiView.vue` | 胸/肺标题；**移除 AI 报告区块**（仅掩码+三视图） |
| `.env.development` | `VITE_USE_MOCK=false` |

### 6.4 数据与脚本

| 文件 | 变更 |
|------|------|
| `docs/sql/seed-demo-check.sql` | 新增 **#62002** 肺部 CT |
| `docs/sql/seed-dict.sql` | `CHK-CT-LUNG` |
| `scripts/install-model-weights.ps1` | **新增** 权重安装 |
| `scripts/start-r-pacs-ai.bat` | 修复编码；一键启 Java + hospital-ai |
| `scripts/seed-demo-check.ps1` | 修复 PowerShell 编码 |
| `scripts/smoke_lung_infer.py` | 肺部推理冒烟（可选） |
| `shared/model-weights/` | **新增** 两个 `.pth` |

### 6.5 刻意未改

- auth / gateway / his / management 业务逻辑
- 前端路由与其他页面
- `TUMOR_SEG`（仍为 STUB）
- 队列「录入结果」里的 AI 报告栏（留给大模型组）

### 6.6 2026-06-22 — PACS 队列与影像工作台（前端 only）

| 场景 | 改动 |
|------|------|
| CHECK + **status=20** | 操作列**只保留「开始执行」**；先 execute（20→30）再跳转影像 AI 工作台 |
| CHECK + **status=40** | 新增 **「查看影像」**（`view=1` 查看模式，自动加载 MinIO 预览） |
| **查看模式** | 无「录入结果」按钮 |
| **工作台（非查看）** | 右上角 **「录入结果」**，与队列弹窗相同 |
| 肺部标题 | **「肺部 CT 伪影检测」** |

| 文件 | 变更 |
|------|------|
| `TechQueuePanel.vue` | `onExecuteAndGoImaging`；status 20/30/40 按钮 |
| `ImagingAiView.vue` | 查看模式自动加载；工作台录入结果；标题文案 |
| `docs/sql/seed-demo-check-extra.sql` | 演示单 #62001–#62006（status=20，本地可选） |

---

## 七、验收步骤

1. `GET http://127.0.0.1:8000/v1/health` → 双模型 `true`，`device: cuda`
2. **#62002 肺部 CT** → 上传 NIfTI 或 DICOM → AI 检测 → 三视图 + 掩码叠加
3. **#62001 头部 CT** → 同上，回归头部
4. 工作台**不应**再出现 CNN 生成的文字报告块
5. MinIO：`studies/62001/`、`studies/62002/` 各有 `source/`、`mask.nii.gz`
6. **PACS 队列**：status=20 仅「开始执行」且自动进工作台；status=40 有「查看影像」并自动加载预览
7. **查看模式**（`view=1`）无「录入结果」；执行/工作台模式右上角有「录入结果」

---

## 八、相关文档索引

| 文档 | 读者 | 内容 |
|------|------|------|
| [AI_CNN_INTEGRATION.md](./AI_CNN_INTEGRATION.md) | CNN / pacs 联调 | 架构、启动、验收、权重 §十一 |
| [IMAGING_DATA_ACCESS.md](./IMAGING_DATA_ACCESS.md) | his / 患者端 / 其他后端 | **如何通过 API 拿 MinIO 影像**（勿直连 MinIO） |
| [LOCAL_WORKSPACE.md](./LOCAL_WORKSPACE.md) | 全员 | 本机路径、端口、日常流程 |
| [LUNG_MODEL_INTEGRATION.md](./LUNG_MODEL_INTEGRATION.md) | wsh / 复现 | 肺部权重部署细节 |
| [LUNG_CT_DATA_PLAN.md](./LUNG_CT_DATA_PLAN.md) | 数据采集 | LIDC 与标注计划 |
| [AI_TASK_TYPE_MINIMAL.md](./AI_TASK_TYPE_MINIMAL.md) | 架构 | taskType 三态设计 |

---

## 九、训练侧（不在 Git 仓库）

| 项 | 路径 |
|----|------|
| 头部训练 | `6.3/BrainCT` |
| 肺部训练 | `6.3/BrainCT-Lung` |
| 肺部实验说明 | `6.3/BrainCT-Lung/肺部实验说明.md` |

---

## 十、已知限制

- MinIO 数据在**各同学本机**，不随 Git 同步；要看同一份影像需共用服务器或 API
- 首次 `git pull` 含约 66MB 权重，网络慢时多等一会
- RTX 50 系需 cu128 PyTorch，见 `scripts/install-gpu-torch.ps1`
