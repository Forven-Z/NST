from __future__ import annotations

from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.config import settings
from app.jobs import STATUS_FAILED, STATUS_SUCCEEDED, get_infer, job_store, submit_job

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


@app.on_event("startup")
def warmup_model():
    try:
        get_infer()
        print("✅ hospital-ai 模型预热完成")
    except Exception as exc:
        print(f"❌ hospital-ai 模型加载失败: {exc}")
        raise


@app.get("/v1/health")
def health() -> dict[str, Any]:
    infer = get_infer()
    return {
        "status": "UP",
        "service": "hospital-ai",
        "modelLoaded": infer is not None,
        "device": str(infer.device),
    }


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
