"""本地冒烟：MinIO 上传 NIfTI → LUNG_CT_ARTIFACT 推理任务。"""
from __future__ import annotations

import json
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

import numpy as np
import SimpleITK as sitk
from minio import Minio

ROOT = Path(__file__).resolve().parents[1] / "hospital-ai"
sys.path.insert(0, str(ROOT))


def main() -> int:
    endpoint = "127.0.0.1:9001"
    client = Minio(endpoint, access_key="minioadmin", secret_key="minioadmin123", secure=False)
    bucket = "imaging"
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)

    arr = np.random.randint(-200, 800, (8, 256, 256), dtype=np.int16)
    img = sitk.GetImageFromArray(arr)
    img.SetSpacing((1.0, 1.0, 2.0))
    with tempfile.TemporaryDirectory() as td:
        local = Path(td) / "lung_test.nii.gz"
        sitk.WriteImage(img, str(local))
        key = "studies/smoke62002/source/lung_test.nii.gz"
        client.fput_object(bucket, key, str(local), content_type="application/gzip")

    body = {
        "studyId": 99999,
        "checkRequestId": 62002,
        "source": {"bucket": bucket, "objectKeyPrefix": "studies/smoke62002/source"},
        "resultPrefix": "studies/smoke62002/ai",
        "callbackUrl": "http://127.0.0.1:9104/internal/imaging/callback",
        "taskType": "LUNG_CT_ARTIFACT",
    }

    def http_json(method: str, url: str, payload: dict | None = None) -> dict:
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data,
            method=method,
            headers={"Content-Type": "application/json"} if payload is not None else {},
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))

    created = http_json("POST", "http://127.0.0.1:8000/v1/inference/jobs", body)
    job_id = created["jobId"]
    print("jobId:", job_id)

    for i in range(120):
        j = http_json("GET", f"http://127.0.0.1:8000/v1/inference/jobs/{job_id}")
        status = j.get("status")
        print(f"poll {i}: {status}")
        if status == "SUCCEEDED":
            result = j.get("result", {})
            print("modality:", result.get("reportJson", {}).get("modality"))
            print("maskKey:", result.get("maskObjectKey"))
            print("OK lung smoke test passed")
            return 0
        if status == "FAILED":
            print("FAILED:", j.get("errorMessage"))
            return 1
        time.sleep(2)

    print("timeout")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
