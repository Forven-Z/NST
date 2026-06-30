# 肺部数据下载完成后 — 标注与训练清单

> **前置**：LIDC DICOM 已落到 `6.3/BrainCT-Lung/Datasets/raw_dicom/lidc/`  
> **详细数据获取**：`6.3/BrainCT-Lung/docs/胸部CT数据获取.md`  
> **集成计划**：`LUNG_CT_DATA_PLAN.md`

---

## 一、确认下载完成

PowerShell：

```powershell
$root = "C:\Neuedu\6.3\BrainCT-Lung\Datasets\raw_dicom\lidc"
(Get-ChildItem $root -Recurse -Filter "*.dcm").Count
(Get-ChildItem $root -Recurse -Filter "*.dcm.tmp").Count
```

- `.dcm` 数量应持续增加，`.tmp` 应接近 **0**
- TCIA Retriever 界面 **0%** 可忽略，以硬盘文件为准

---

## 二、筛病例（10～15 例）

1. 打开 `ct_mask_gui`：

   ```powershell
   cd C:\Neuedu\6.3\BrainCT-Lung\ct_mask_gui
   python run_mask_gui.py
   ```

2. **肺窗**（WW≈1500 / WL≈-600）浏览 `raw_dicom/lidc` 下各病例文件夹  
3. 优先保留：牙弓/金属高亮/条纹伪影明显的序列  
4. 记录病例 ID 列表（如 LIDC-IDRI-0001 …）

---

## 三、转 2D + 标注

与头部 `BrainCT` 相同流程：

```text
选定病例 DICOM
    → Split3D2Dcm.py 导出 2D
    → ct_mask_gui 标 MASK（保存到 Datasets/CT 与 Datasets/MASK，文件名一一对应）
    → 目标 ≥500 对切片
```

`Conf/Config.py` 已指向 `Datasets/CT`、`Datasets/MASK`，权重输出 `lung_artifact_best.pth`。

---

## 四、训练与接入 NST-work

```powershell
cd C:\Neuedu\6.3\BrainCT-Lung
python Main.py
```

权重拷贝：

```text
run/weights/lung_artifact_best.pth
  → C:\Neuedu\NST-work\hospital-ai\model\weights\lung_artifact_best.pth
```

在 `hospital-ai/.env` 中确认路径后重启即可（见 `docs/LUNG_MODEL_INTEGRATION.md`）。

---

## 五、联调自测

| 检查单 | 预期 |
|--------|------|
| 62001 头部 CT | 掩码成功 |
| 62002 肺部 CT | 有肺部权重后成功；无权重时 STUB 明确失败 |

启动顺序见 `LOCAL_WORKSPACE.md`、`AI_CNN_INTEGRATION.md`。
