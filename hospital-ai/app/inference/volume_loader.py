"""自 6.8/Utils/volume_loader.py 迁入。"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import SimpleITK as sitk


def load_dicom_series(folder: Path | str, *, pick_largest_series: bool = True) -> tuple[sitk.Image, str, int]:
    folder = Path(folder)
    reader = sitk.ImageSeriesReader()
    series_ids = reader.GetGDCMSeriesIDs(str(folder)) or []
    best_names: list[str] = []
    best_series_id = ""
    if series_ids and pick_largest_series:
        for sid in series_ids:
            names = reader.GetGDCMSeriesFileNames(str(folder), sid)
            if len(names) > len(best_names):
                best_names = list(names)
                best_series_id = sid
    elif series_ids:
        best_series_id = series_ids[0]
        best_names = list(reader.GetGDCMSeriesFileNames(str(folder), best_series_id))
    if not best_names:
        dcms = sorted(folder.glob("*.dcm")) + sorted(folder.glob("*.DCM"))
        if not dcms:
            raise ValueError(f"未找到 DICOM 序列: {folder}")
        best_names = [str(f) for f in dcms]
    reader.SetFileNames(best_names)
    image = reader.Execute()
    return image, best_series_id, len(best_names)


def _downsample_for_web_preview(
    image: sitk.Image,
    *,
    max_edge: int = 256,
    max_slices: int = 96,
    interpolator=sitk.sitkLinear,
) -> sitk.Image:
    """缩小预览体积，避免浏览器 Niivue 重复解析大文件时卡死。"""
    size = list(image.GetSize())
    spacing = list(image.GetSpacing())
    if len(size) < 3:
        return image

    factor = [1.0, 1.0, 1.0]
    longest = max(size[0], size[1])
    if longest > max_edge:
        scale = max_edge / float(longest)
        factor[0] = factor[1] = scale
    if size[2] > max_slices:
        factor[2] = max_slices / float(size[2])

    if factor == [1.0, 1.0, 1.0]:
        return image

    new_size = [
        max(1, int(round(size[0] * factor[0]))),
        max(1, int(round(size[1] * factor[1]))),
        max(1, int(round(size[2] * factor[2]))),
    ]
    new_spacing = [
        spacing[0] / factor[0],
        spacing[1] / factor[1],
        spacing[2] / factor[2],
    ]
    resampler = sitk.ResampleImageFilter()
    resampler.SetInterpolator(interpolator)
    resampler.SetOutputSpacing(new_spacing)
    resampler.SetSize(new_size)
    resampler.SetOutputDirection(image.GetDirection())
    resampler.SetOutputOrigin(image.GetOrigin())
    return resampler.Execute(image)


def save_preview_nifti(image: sitk.Image, path: Path | str) -> str:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    preview = _downsample_for_web_preview(image)
    sitk.WriteImage(preview, str(path))
    return str(path)


def save_mask_preview_nifti(mask_image: sitk.Image, path: Path | str) -> str:
    """掩码预览用最近邻降采样，减小浏览器 Niivue 解析压力。"""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    preview = _downsample_for_web_preview(mask_image, interpolator=sitk.sitkNearestNeighbor)
    sitk.WriteImage(preview, str(path))
    return str(path)


def mask_slice_indices(mask_image: sitk.Image) -> list[int]:
    arr = sitk.GetArrayFromImage(mask_image)
    if arr.size == 0:
        return []
    return [int(i) for i in range(arr.shape[0]) if np.any(arr[i])]


def mask_voxel_count(mask_image: sitk.Image) -> int:
    arr = sitk.GetArrayFromImage(mask_image)
    if arr.size == 0:
        return 0
    return int(np.count_nonzero(arr))
