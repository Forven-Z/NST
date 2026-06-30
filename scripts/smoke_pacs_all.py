"""PACS 全链路冒烟：登录 → AI 报告 → 预览 URL。"""
from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request

from minio import Minio
from minio.commonconfig import CopySource

GATEWAY = "http://127.0.0.1:9000/api/v1"
PSQL = r"C:\Program Files\PostgreSQL\16\bin\psql.exe"
CASES = [
    (62001, "HEAD_CT_ARTIFACT", "CT_HEAD", "1"),
    (62002, "LUNG_CT_ARTIFACT", "CT_LUNG", None),
    (62006, "TUMOR_SEG", "TUMOR_SEG", "62002"),
]


def http_json(method: str, url: str, payload: dict | None = None, token: str | None = None) -> dict:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    with urllib.request.urlopen(req, timeout=300) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    if body.get("code") not in (None, 200, "200", 0):
        raise RuntimeError(f"API error {url}: {body}")
    return body.get("data", body)


def psql(sql: str) -> None:
    proc = subprocess.run(
        [PSQL, "-U", "postgres", "-d", "hospital", "-c", sql],
        capture_output=True,
        text=True,
        env={**dict(os.environ), "PGPASSWORD": "123456"},
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr or proc.stdout)


def copy_minio_prefix(client: Minio, bucket: str, src_prefix: str, dst_prefix: str) -> int:
    n = 0
    for obj in client.list_objects(bucket, prefix=src_prefix, recursive=True):
        if obj.object_name.endswith("/"):
            continue
        suffix = obj.object_name[len(src_prefix) :]
        dst_key = dst_prefix + suffix
        client.copy_object(bucket, dst_key, CopySource(bucket, obj.object_name))
        n += 1
    return n


def ensure_source(client: Minio, check_id: int, copy_from: str | None) -> None:
    prefix = f"studies/{check_id}/source/"
    existing = list(client.list_objects("imaging", prefix=prefix, recursive=True))
    if existing:
        print(f"  source already exists ({len(existing)} objects)")
        return
    if not copy_from:
        raise RuntimeError(f"studies/{check_id}/source/ missing and no copy_from")
    src = f"studies/{copy_from}/source/"
    n = copy_minio_prefix(client, "imaging", src, prefix)
    print(f"  copied {n} objects {src} -> {prefix}")


def ensure_imaging_study(check_id: int, force_reset: bool) -> None:
    if not force_reset:
        out = subprocess.run(
            [PSQL, "-U", "postgres", "-d", "hospital", "-t", "-A", "-c",
             f"SELECT status FROM imaging_study WHERE check_request_id = {check_id} ORDER BY id DESC LIMIT 1;"],
            capture_output=True,
            text=True,
            env={**dict(os.environ), "PGPASSWORD": "123456"},
        )
        if (out.stdout or "").strip() == "COMPLETED":
            print("  imaging_study already COMPLETED")
            return
    psql(
        f"""
        DELETE FROM imaging_study WHERE check_request_id = {check_id};
        INSERT INTO imaging_study (
            study_no, check_request_id, register_id, patient_id, modality, status,
            source_bucket, source_object_key, create_time, update_time
        )
        SELECT
            'ST{check_id}', cr.id, cr.register_id, cr.patient_id,
            CASE cr.id
                WHEN 62001 THEN 'CT_HEAD'
                WHEN 62002 THEN 'CT_LUNG'
                WHEN 62006 THEN 'TUMOR_SEG'
            END,
            'PENDING', 'imaging', 'studies/{check_id}/source/', NOW(), NOW()
        FROM check_request cr
        WHERE cr.id = {check_id};
        UPDATE check_request SET status = 30, executor_id = 3 WHERE id = {check_id};
        """
    )


def verify_preview(token: str, detail: dict) -> bool:
    mask_url = detail.get("maskPreviewUrl")
    ct_url = detail.get("ctPreviewUrl")
    if not mask_url or not ct_url:
        return False
    for kind, url in [("ct", ct_url), ("mask", mask_url)]:
        req = urllib.request.Request(
            f"http://127.0.0.1:9000{url}",
            headers={"Authorization": f"Bearer {token}"},
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            size = len(resp.read())
        print(f"  preview {kind}: {size} bytes")
    print(f"  OK taskType={detail.get('taskType')} mask={mask_url}")
    return True


def main() -> int:
    print("=== login check01 ===")
    login = http_json("POST", f"{GATEWAY}/auth/staff/login", {"username": "check01", "password": "123456"})
    token = login["accessToken"]
    print("  OK token acquired")

    client = Minio("127.0.0.1:9001", access_key="minioadmin", secret_key="minioadmin123", secure=False)
    ok = 0
    for check_id, task_type, _expect_modality, copy_from in CASES:
        print(f"\n=== PACS #{check_id} ({task_type}) ===")
        try:
            ensure_source(client, check_id, copy_from)
            ensure_imaging_study(check_id, force_reset=(check_id != 62002))
            t0 = time.time()
            if check_id == 62002:
                detail = http_json("GET", f"{GATEWAY}/pacs/requests/{check_id}/imaging-preview", token=token)
            else:
                detail = http_json("POST", f"{GATEWAY}/pacs/requests/{check_id}/ai-report", token=token)
                if not detail.get("maskPreviewUrl"):
                    detail = http_json("GET", f"{GATEWAY}/pacs/requests/{check_id}/imaging-preview", token=token)
            elapsed = time.time() - t0
            if detail.get("taskType") != task_type:
                print(f"  WARN taskType={detail.get('taskType')} expected={task_type}")
            if verify_preview(token, detail):
                print(f"  elapsed={elapsed:.1f}s")
                ok += 1
            else:
                print(f"  FAIL preview missing: {json.dumps(detail, ensure_ascii=False)[:300]}")
        except (urllib.error.URLError, RuntimeError, Exception) as exc:
            print(f"  FAIL {exc}")

    print(f"\n=== PACS summary: {ok}/{len(CASES)} passed ===")
    return 0 if ok == len(CASES) else 1


if __name__ == "__main__":
    raise SystemExit(main())
