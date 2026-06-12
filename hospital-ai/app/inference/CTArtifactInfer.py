"""自 6.8/Detection/CTArtifactInfer.py 迁入，import 路径适配 hospital-ai。"""
import os

import numpy as np
import SimpleITK as sitk
import torch
from tqdm import tqdm

from model.Config import DEVICE, PRED_THRESHOLD, WEIGHT_FILE
from model.AttentionUNet2D import UNet2D

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_WEIGHT_PATH = os.path.join(ROOT_DIR, "model", "weights", WEIGHT_FILE)


class CTArtifactInfer:
    def __init__(self, model_weight_path=None, device=None, threshold=None):
        self.device = device if device is not None else DEVICE
        self.threshold = float(threshold if threshold is not None else PRED_THRESHOLD)
        self.model_weight_path = model_weight_path or DEFAULT_WEIGHT_PATH
        if not os.path.isfile(self.model_weight_path):
            raise FileNotFoundError(f"未找到权重文件: {self.model_weight_path}")
        self.model = self._load_model()

    def _load_model(self):
        model = UNet2D().to(self.device)
        state = torch.load(self.model_weight_path, map_location=self.device)
        model.load_state_dict(state)
        model.eval()
        print(f"[Infer] 已加载权重: {self.model_weight_path} | device={self.device}")
        return model

    def predict_slice(self, img_slice, save_feature_path=None):
        img_slice = img_slice.astype(np.float32)
        mean = img_slice.mean()
        std = img_slice.std()
        img_slice = (img_slice - mean) / (std + 1e-7)
        tensor = torch.from_numpy(img_slice).unsqueeze(0).unsqueeze(0).to(self.device)
        with torch.no_grad():
            output = self.model(tensor)
            pred = torch.sigmoid(output).squeeze().cpu().numpy()
            pred_mask = ((pred > self.threshold).astype(np.uint8) * 255)
            if save_feature_path is not None:
                feat = self.model.extract_features().squeeze(0).cpu().numpy()
                out_dir = os.path.dirname(save_feature_path)
                if out_dir and not os.path.exists(out_dir):
                    os.makedirs(out_dir, exist_ok=True)
                np.save(save_feature_path, feat)
        return pred_mask

    def predict_from_sitk(self, sitk_ct, save_mask_path=None):
        ct_vol = sitk.GetArrayFromImage(sitk_ct)
        d, _, _ = ct_vol.shape
        mask_vol = np.zeros((d, ct_vol.shape[1], ct_vol.shape[2]), dtype=np.uint8)
        for z in tqdm(range(d), desc="CNN 推理"):
            mask_vol[z] = self.predict_slice(ct_vol[z])
        sitk_mask = sitk.GetImageFromArray(mask_vol)
        sitk_mask.CopyInformation(sitk_ct)
        sitk_mask = sitk.Cast(sitk_mask, sitk.sitkUInt8)
        if save_mask_path:
            output_dir = os.path.dirname(save_mask_path)
            if output_dir and not os.path.exists(output_dir):
                os.makedirs(output_dir, exist_ok=True)
            sitk.WriteImage(sitk_mask, save_mask_path)
        return sitk_mask
