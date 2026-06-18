# 肺部模型接入 hospital-ai

> **当前**：`lung_artifact_best.pth` 已部署；`LUNG_CT_ARTIFACT` 可正常推理。  
> **无权重时**：仍返回 STUB 明确失败（不会误用头部 `best.pth`）。

---

## 一、权重放置

```text
NST-work/hospital-ai/model/weights/lung_artifact_best.pth
```

结构与头部 `best.pth` 相同（Attention UNet 2D state_dict）。

---

## 二、环境变量（可选）

`hospital-ai/.env`：

```text
MODEL_WEIGHT_PATH=model/weights/best.pth
LUNG_MODEL_WEIGHT_PATH=model/weights/lung_artifact_best.pth
```

未设置时默认即为上述路径。

---

## 三、行为说明

| taskType | 权重 | 无权重时 |
|----------|------|----------|
| `HEAD_CT_ARTIFACT` | `best.pth` | 启动失败（必须存在） |
| `LUNG_CT_ARTIFACT` | `lung_artifact_best.pth` | STUB 失败（见 `LUNG_CT_DATA_PLAN.md`） |
| `TUMOR_SEG` | 未实现 | STUB 失败 |

权重存在时，`jobs.py` 按 taskType 选择对应 `CTArtifactInfer` 实例；报告 `modality` 为 `CT_LUNG`。

---

## 四、验证步骤

1. 拷贝权重到 `model/weights/lung_artifact_best.pth`
2. 重启 `scripts\start-r-pacs-ai.bat`
3. `GET http://127.0.0.1:8000/v1/health` → `lungModelLoaded: true`
4. 检查队列 **62002 胸部 CT** → 上传胸部 DICOM → **开始 AI 检测** → 应出掩码与肺部报告文案

头部 **62001** 回归不受影响。

---

## 五、训练侧文档

- `docs/LUNG_INTEGRATION_TEAM_CHANGELOG.md` — **小组变更说明（推荐阅读）**
- `6.3/BrainCT-Lung/肺部实验说明.md`
- `6.3/BrainCT-Lung/docs/胸部CT数据获取.md`
- `docs/LUNG_ANNOTATION_NEXT.md`
