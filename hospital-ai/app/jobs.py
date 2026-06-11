from __future__ import annotations

import threading
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import SimpleITK as sitk

from app.config import ROOT_DIR, settings
from app.inference.CTArtifactInfer import CTArtifactInfer
from app.inference.volume_loader import (
    load_dicom_series,
    mask_slice_indices,
    mask_voxel_count,
    save_mask_preview_nifti,
    save_preview_nifti,
)
from app.minio_client import MinioStorage
from app.pacs_callback import post_callback

STATUS_PENDING = "PENDING"
STATUS_RUNNING = "RUNNING"
STATUS_SUCCEEDED = "SUCCEEDED"
STATUS_FAILED = "FAILED"


@dataclass
class InferenceJob:
    job_id: str
    study_id: int
    check_request_id: int
    source_bucket: str
    source_object_key_prefix: str
    result_prefix: str
    callback_url: str
    status: str = STATUS_PENDING
    error_message: str | None = None
    result: dict[str, Any] | None = None
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    updated_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


class JobStore:
    def __init__(self):
        self._jobs: dict[str, InferenceJob] = {}
        self._lock = threading.Lock()

    def create(self, **kwargs) -> InferenceJob:
        job = InferenceJob(job_id=str(uuid.uuid4()), **kwargs)
        with self._lock:
            self._jobs[job.job_id] = job
        return job

    def get(self, job_id: str) -> InferenceJob | None:
        with self._lock:
            return self._jobs.get(job_id)

    def update(self, job: InferenceJob):
        job.updated_at = datetime.now(timezone.utc).isoformat()
        with self._lock:
            self._jobs[job.job_id] = job


job_store = JobStore()
_infer: CTArtifactInfer | None = None
_minio: MinioStorage | None = None


def get_infer() -> CTArtifactInfer:
    global _infer
    if _infer is None:
        _infer = CTArtifactInfer(model_weight_path=settings.model_weight_path)
    return _infer


def get_minio() -> MinioStorage:
    global _minio
    if _minio is None:
        _minio = MinioStorage()
    return _minio


def build_ai_report_text(report_json: dict) -> str:
    count = int(report_json.get("maskVoxelCount") or 0)
    slices = report_json.get("maskSliceIndices") or []
    slice_count = report_json.get("sliceCount") or "-"
    if count <= 0:
        return "AI 影像分析完成：未检测到明显金属伪影区域。"
    labels = [str(int(z) + 1) for z in slices[:12]]
    suffix = f" 等共 {len(slices)} 层" if len(slices) > 12 else ""
    return (
        f"AI 影像分析完成：检测到金属伪影相关区域，伪影像素数 {count}。"
        f"序列共 {slice_count} 层。主要累及轴位第 {', '.join(labels)}{suffix}。"
        f"建议结合临床病史与原始影像综合判读。"
    )


def _load_volume_from_dir(source_dir: Path) -> sitk.Image:
    nifti_files = sorted(source_dir.rglob("*.nii")) + sorted(source_dir.rglob("*.nii.gz"))
    if nifti_files:
        return sitk.ReadImage(str(nifti_files[0]))
    image, _, _ = load_dicom_series(source_dir)
    return image


def run_inference_job(job: InferenceJob):
    job.status = STATUS_RUNNING
    job_store.update(job)
    tmp_root = ROOT_DIR / "tmp" / job.job_id
    source_dir = tmp_root / "source"
    output_dir = tmp_root / "output"
    try:
        minio = get_minio()
        infer = get_infer()
        minio.download_prefix(job.source_object_key_prefix, source_dir)
        sitk_ct = _load_volume_from_dir(source_dir)
        output_dir.mkdir(parents=True, exist_ok=True)
        mask_local = output_dir / "mask.nii.gz"
        preview_local = output_dir / "ct_preview.nii.gz"
        mask_sitk = infer.predict_from_sitk(sitk_ct, save_mask_path=str(output_dir / "mask_full.nii.gz"))
        save_preview_nifti(sitk_ct, preview_local)
        save_mask_preview_nifti(mask_sitk, mask_local)
        prefix = job.result_prefix if job.result_prefix.endswith("/") else job.result_prefix + "/"
        mask_key = f"{prefix}mask.nii.gz"
        preview_key = f"{prefix}ct_preview.nii.gz"
        minio.upload_file(mask_key, mask_local, content_type="application/gzip")
        minio.upload_file(preview_key, preview_local, content_type="application/gzip")
        spacing = sitk_ct.GetSpacing()
        report_json = {
            "maskVoxelCount": mask_voxel_count(mask_sitk),
            "maskSliceIndices": mask_slice_indices(mask_sitk),
            "sliceCount": int(sitk_ct.GetSize()[2]) if sitk_ct.GetDimension() >= 3 else 1,
            "spacing": [float(spacing[0]), float(spacing[1]), float(spacing[2])],
            "modality": "CT",
        }
        ai_report_text = build_ai_report_text(report_json)
        result = {
            "maskBucket": settings.minio_bucket,
            "maskObjectKey": mask_key,
            "previewBucket": settings.minio_bucket,
            "previewObjectKey": preview_key,
            "reportJson": report_json,
            "aiReportText": ai_report_text,
        }
        job.status = STATUS_SUCCEEDED
        job.result = result
        job_store.update(job)
        post_callback(
            {
                "jobId": job.job_id,
                "studyId": job.study_id,
                "checkRequestId": job.check_request_id,
                "status": STATUS_SUCCEEDED,
                "result": result,
                "errorMessage": None,
            },
            job.callback_url,
        )
    except Exception as exc:
        job.status = STATUS_FAILED
        job.error_message = str(exc)
        job_store.update(job)
        try:
            post_callback(
                {
                    "jobId": job.job_id,
                    "studyId": job.study_id,
                    "checkRequestId": job.check_request_id,
                    "status": STATUS_FAILED,
                    "result": None,
                    "errorMessage": str(exc),
                },
                job.callback_url,
            )
        except Exception:
            pass
    finally:
        if tmp_root.exists():
            import shutil
            shutil.rmtree(tmp_root, ignore_errors=True)


def submit_job(**kwargs) -> InferenceJob:
    job = job_store.create(**kwargs)
    threading.Thread(target=run_inference_job, args=(job,), daemon=True).start()
    return job
