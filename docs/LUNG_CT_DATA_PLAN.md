# 肺部 CT 金属伪影 — 数据与训练计划（无现成数据集时）

> **负责人**：wsh  
> **状态**：数据待采集；集成侧已支持 `LUNG_CT_ARTIFACT`（模型未就绪时 STUB 明确失败）  
> **训练工程建议目录**：`C:\Neuedu\6.3\BrainCT-Lung`（从 `BrainCT` 复制后改配置，与头部并行）

---

## 一、现状

- **头部**：40 例、约 1862 对切片，Attention UNet Dice ≈ 0.57，权重 `best.pth` 已用于 `hospital-ai`。
- **肺部**：**没有**已标注的肺部 CT 金属伪影数据集。
- **集成**：`taskType=LUNG_CT_ARTIFACT` 已可识别；无权重时系统返回「肺部模型尚未部署」，**不会误用头部权重**。

---

## 二、没有数据集时，分三阶段做

### 阶段 1 — 现在就能做（不依赖新数据）

| 事项 | 说明 |
|------|------|
| taskType 分发 | 已完成：检查项目「肺部 CT」→ `CT_LUNG` → `LUNG_CT_ARTIFACT` |
| 训练工程骨架 | 复制 `6.3/BrainCT` → `6.3/BrainCT-Lung`，只改 `Config.py` 路径与实验名 |
| 标注规范 | 沿用老师 HU 参考 + 肺窗（WW/WL 约 1500/-600）看伪影 |
| 答辩表述 | 「框架与任务分发已通，肺部模型待数据里程碑」 |

### 阶段 2 — 老师也没有现成数据时

老师/东软没有标注好的肺部伪影库，**不代表做不了**——用 **LIDC-IDRI 公开肺部 CT** 自标掩码（详见 `6.3/BrainCT-Lung/docs/胸部CT数据获取.md`）。

> **更正**：`C:\Neuedu\CQ500_orig` 是 **头部 CT**（491 例颅脑 CT），**不能用于肺部训练**。`data.zip` / `dicom2d` 也是头部 40 例训练数据。

| 优先级 | 来源 | 说明 |
|--------|------|------|
| **P0（推荐）** | **LIDC-IDRI（TCIA）** | 1010 例**肺部** CT，NBIA 只下 10～15 例即可（约几 GB，非全库 125GB） |
| **P1** | 东软 / 实习医院 | 脱敏 DICOM，与 LIDC 合并 |
| **P2** | 组内同学素材 | 合并进 `BrainCT-Lung/datasets` |
| **P3** | 临时演示 | 仅验证 taskType 链路，不能当肺部模型成果 |

**本机已有数据对照**

| 目录 | 部位 | 能否训肺部 |
|------|------|------------|
| `CQ500_orig` | **头部** | ❌ |
| `6.3/BrainCT` / `dicom2d` / `nifti3d` | **头部**（40 例训练用） | ❌ |
| 待采集肺部 DICOM | **肺部** | ✅ |

**落地步骤（有肺部 CT 后）** — 完整操作见 `6.3/BrainCT-Lung/docs/胸部CT数据获取.md`

```text
1. TCIA 下载 10～15 例 LIDC-IDRI 肺部 DICOM
2. 筛含金属高亮/条纹伪影的序列
3. Split3D2Dcm → ct_mask_gui 标 MASK（肺窗）
4. 目标 ≥500 对 CT/MASK → python Main.py
5. 权重 → hospital-ai/model/weights/lung_artifact_best.pth
```

**答辩表述（老师无数据版）**：

> 临床侧无现成标注库；计划采用肺部 CT（医院脱敏或公开肺部 CT 库）按与头部相同的 HU 规范自标注金属伪影掩码。集成 taskType 已通，无权重阶段 STUB 明确失败。

### 阶段 2b — 若仍想争取外部数据（备选）

| 优先级 | 来源 | 做法 |
|--------|------|------|
| P0 | 东软 / 其他医院合作 | 脱敏 DICOM，哪怕 5 例也可开训 |
| P1 | 同组同学影像素材 | 合并进 BrainCT-Lung/datasets |

**重要**：公开数据集几乎**没有**「金属伪影掩码」 gold standard，**无法直接下载就能训**。必须 **肺部 CT + 人工掩码** 成对数据。

### 阶段 3 — 有数据后

1. 目标规模：**先 15～20 例** 肺部病例，能配成 **≥500 对** 有效切片即可开训（与头部起步类似）。
2. 目录：

```text
6.3/BrainCT-Lung/
  Datasets/CT/
  Datasets/MASK/
  run/weights/lung_artifact_best.pth
```

3. 拷贝权重到：

```text
NST-work/hospital-ai/model/weights/lung_artifact_best.pth
```

4. 去掉 Python 里 `LUNG_CT_ARTIFACT` 的 STUB，改为加载肺部权重。

---

## 三、复制训练工程（现在可执行）

在 PowerShell 中：

```powershell
# 复制头部工程为肺部专用（不覆盖原 BrainCT）
xcopy /E /I C:\Neuedu\6.3\BrainCT C:\Neuedu\6.3\BrainCT-Lung

# 然后在 BrainCT-Lung\Conf\Config.py 中修改：
#   DATA_ROOT → datasets 路径
#   RUN_NAME  → lung_artifact
#   窗宽窗位说明写入 实验说明.md（肺窗）
```

数据到位后，按与头部相同流程：`Split3D2Dcm` → 标注（ct_mask_gui）→ `python Main.py`。

---

## 四、向老师 / 组长要数据时怎么说

可参考：

> 肺部 CT 金属伪影任务需要 **成对的 CT 与伪影掩码**（约 15～20 例肺部 CT，含金属植入或牙科等高亮伪影）。公开库没有现成标注，希望提供脱敏 DICOM 或指导从哪个病例库筛选。集成与 taskType 已就绪，有数据后 1～2 周可出第一版 `lung_best.pth`。

---

## 五、各阶段验收标准

| 阶段 | 验收 |
|------|------|
| 集成 | 开「肺部 CT」检查单 → AI 分析 → 明确提示模型未部署或失败原因 |
| 数据 | `datasets/CT` 与 `MASK` 文件名一一对应，数量一致 |
| 训练 | `lung_best.pth` 存在，验证 Dice 记录在 `training_history.json` |
| 上线 | 肺部 CT 检查可出掩码 + 肺部报告文案，不再 STUB |

---

## 六、与肿瘤分割的关系

肿瘤分割需要 **病灶掩码**，与伪影标注不同，数据更难。建议：**肺部伪影先走通** → 再单独要肿瘤 CT 标注或缩小为「单病灶二值分割」_demo 例。

---

*数据未到位前，以本文档 + `AI_TASK_TYPE_MINIMAL.md` 的 STUB 行为作为答辩与联调基线。*

---

## 七、相关文档

| 文件 | 说明 |
|------|------|
| `docs/LUNG_ANNOTATION_NEXT.md` | LIDC 下载完成后的标注 / 训练清单 |
| `docs/LUNG_MODEL_INTEGRATION.md` | 权重就位后 hospital-ai 热插拔说明 |
| `6.3/BrainCT-Lung/docs/胸部CT数据获取.md` | LIDC 下载、TCIA 排错、标注流程 |
| `docs/AI_TASK_TYPE_MINIMAL.md` | taskType 第二步代码改动说明 |
| `docs/LOCAL_WORKSPACE.md` | 本机 MinIO / PG / GPU 路径 |
