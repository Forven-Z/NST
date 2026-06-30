"""冒烟：头部 / 肺部 / 肿瘤 三个 taskType 推理。"""
from __future__ import annotations

import json
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path

import numpy as np
import SimpleITK as sitk
from minio import Minio

CASES = [
    ("HEAD_CT_ARTIFACT", "smoke62001", 62001, "CT_HEAD"),
    ("LUNG_CT_ARTIFACT", "smoke62002", 62002, "CT_LUNG"),
    ("TUMOR_SEG", "smoke62006", 62006, "TUMOR_SEG"),
]


def http_json(method: str, url: str, payload: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Content-Type": "application/json"} if payload is not None else {},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def run_case(client: Minio, bucket: str, task_type: str, prefix: str, check_id: int, expect_modality: str) -> bool:
    arr = np.random.randint(-200, 800, (8, 256, 256), dtype=np.int16)
    img = sitk.GetImageFromArray(arr)
    img.SetSpacing((1.0, 1.0, 2.0))
    with tempfile.TemporaryDirectory() as td:
        local = Path(td) / "volume.nii.gz"
        sitk.WriteImage(img, str(local))
        key = f"studies/{prefix}/source/volume.nii.gz"
        client.fput_object(bucket, key, str(local), content_type="application/gzip")

    body = {
        "studyId": check_id,
        "checkRequestId": check_id,
        "source": {"bucket": bucket, "objectKeyPrefix": f"studies/{prefix}/source"},
        "resultPrefix": f"studies/{prefix}/ai",
        "callbackUrl": "http://127.0.0.1:9104/internal/imaging/callback",
        "taskType": task_type,
    }
    created = http_json("POST", "http://127.0.0.1:8000/v1/inference/jobs", body)
    job_id = created["jobId"]
    print(f"  jobId={job_id}")

    for i in range(120):
        j = http_json("GET", f"http://127.0.0.1:8000/v1/inference/jobs/{job_id}")
        status = j.get("status")
        if status == "SUCCEEDED":
            report = j.get("result", {}).get("reportJson", {})
            modality = report.get("modality")
            mask_key = j.get("result", {}).get("maskObjectKey")
            voxels = report.get("maskVoxelCount")
            if modality != expect_modality:
                print(f"  FAIL modality={modality} expected={expect_modality}")
                return False
            if not mask_key or not client.stat_object(bucket, mask_key):
                print(f"  FAIL mask missing: {mask_key}")
                return False
            print(f"  OK modality={modality} voxels={voxels} mask={mask_key}")
            return True
        if status == "FAILED":
            print(f"  FAIL {j.get('errorMessage')}")
            return False
        time.sleep(2)

    print("  FAIL timeout")
    return False


def main() -> int:
    health = http_json("GET", "http://127.0.0.1:8000/v1/health")
    print("health:", json.dumps(health, ensure_ascii=False))
    if not health.get("modelLoaded"):
        print("FAIL head model not loaded")
        return 1

    endpoint = "127.0.0.1:9001"
    client = Minio(endpoint, access_key="minioadmin", secret_key="minioadmin123", secure=False)
    bucket = "imaging"
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)

    ok = 0
    for task_type, prefix, check_id, expect_modality in CASES:
        print(f"\n=== {task_type} (#{check_id}) ===")
        try:
            if run_case(client, bucket, task_type, prefix, check_id, expect_modality):
                ok += 1
        except urllib.error.URLError as exc:
            print(f"  FAIL HTTP {exc}")

    print(f"\n=== summary: {ok}/{len(CASES)} passed ===")
    return 0 if ok == len(CASES) else 1


if __name__ == "__main__":
    raise SystemExit(main())
