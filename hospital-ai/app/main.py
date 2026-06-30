from __future__ import annotations

from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.config import settings
from app.inference.task_types import HEAD_CT_ARTIFACT, LUNG_CT_ARTIFACT, TUMOR_SEG
from app.jobs import (
    STATUS_FAILED,
    STATUS_SUCCEEDED,
    _lung_weight_ready,
    _tumor_weight_ready,
    get_infer,
    job_store,
    submit_job,
)

app = FastAPI(title="hospital-ai CNN", version="1.0.0")


class SourceRef(BaseModel):
    bucket: str
    objectKeyPrefix: str


class CreateJobRequest(BaseModel):
    studyId: int
    checkRequestId: int
    source: SourceRef
    resultPrefix: str
    callbackUrl: str | None = None
    taskType: str = "HEAD_CT_ARTIFACT"


@app.on_event("startup")
def warmup_model():
    try:
        get_infer(HEAD_CT_ARTIFACT)
        loaded = ["头部"]
        if _lung_weight_ready():
            get_infer(LUNG_CT_ARTIFACT)
            loaded.append("肺部")
        if _tumor_weight_ready():
            get_infer(TUMOR_SEG)
            loaded.append("肿瘤")
        missing = []
        if not _lung_weight_ready():
            missing.append("肺部")
        if not _tumor_weight_ready():
            missing.append("肿瘤")
        msg = f"✅ hospital-ai {' + '.join(loaded)}模型预热完成"
        if missing:
            msg += f"（{'/'.join(missing)}权重未部署，对应任务将 STUB）"
        print(msg)
    except Exception as exc:
        print(f"❌ hospital-ai 模型加载失败: {exc}")
        raise


@app.get("/v1/health")
def health() -> dict[str, Any]:
    infer = get_infer(HEAD_CT_ARTIFACT)
    lung_ready = _lung_weight_ready()
    tumor_ready = _tumor_weight_ready()
    payload: dict[str, Any] = {
        "status": "UP",
        "service": "hospital-ai",
        "modelLoaded": infer is not None,
        "lungModelLoaded": lung_ready,
        "tumorModelLoaded": tumor_ready,
        "device": str(infer.device),
    }
    if lung_ready:
        lung_infer = get_infer(LUNG_CT_ARTIFACT)
        payload["lungDevice"] = str(lung_infer.device)
    if tumor_ready:
        tumor_infer = get_infer(TUMOR_SEG)
        payload["tumorDevice"] = str(tumor_infer.device)
    return payload


@app.post("/v1/inference/jobs")
def create_job(body: CreateJobRequest) -> dict[str, Any]:
    callback_url = body.callbackUrl or settings.pacs_callback_url
    job = submit_job(
        study_id=body.studyId,
        check_request_id=body.checkRequestId,
        source_bucket=body.source.bucket,
        source_object_key_prefix=body.source.objectKeyPrefix,
        result_prefix=body.resultPrefix,
        callback_url=callback_url,
        task_type=body.taskType,
    )
    return {
        "jobId": job.job_id,
        "studyId": job.study_id,
        "checkRequestId": job.check_request_id,
        "status": job.status,
    }


@app.get("/v1/inference/jobs/{job_id}")
def get_job(job_id: str) -> dict[str, Any]:
    job = job_store.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="job 不存在")
    payload: dict[str, Any] = {
        "jobId": job.job_id,
        "studyId": job.study_id,
        "checkRequestId": job.check_request_id,
        "status": job.status,
        "errorMessage": job.error_message,
        "createdAt": job.created_at,
        "updatedAt": job.updated_at,
    }
    if job.status == STATUS_SUCCEEDED:
        payload["result"] = job.result
    if job.status == STATUS_FAILED:
        payload["failed"] = True
    return payload


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=False)
