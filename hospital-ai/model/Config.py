import os
import sys
import torch

# Windows 终端 UTF-8，避免中文/进度条乱码
def _configure_console():
	if sys.platform == "win32":
		for stream in (sys.stdout, sys.stderr):
			if stream is not None and hasattr(stream, "reconfigure"):
				try:
					stream.reconfigure(encoding="utf-8", errors="replace")
				except Exception:
					pass


_configure_console()

# ====================== 路径配置 ======================
SAVE_DIR = r"./run"
WEIGHT_SAVE_DIR = os.path.join(SAVE_DIR, "weights")
os.makedirs(SAVE_DIR, exist_ok=True)
os.makedirs(WEIGHT_SAVE_DIR, exist_ok=True)
WEIGHT_FILE = "best.pth"
CHECKPOINT_FILE = "checkpoint.pth"
# 无 checkpoint 时从日志恢复；日志不可用则填已完成的最后一轮（你上次停在 67 轮结束、68 轮中途）
RESUME_LAST_EPOCH = 67

CT_PATH = r"datasets/CT"
MASK_PATH = r"datasets/MASK"

# ====================== 训练超参数 ======================
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
BATCH_SIZE = 2
EPOCHS = 100
LEARNING_RATE = 1e-4
VAL_SPLIT = 0.2  # 验证集比例
LR = 1e-4

# 验证与可视化：sigmoid 后二值化阈值
PRED_THRESHOLD = 0.5