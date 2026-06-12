from __future__ import annotations

import shutil
from pathlib import Path

from minio import Minio

from app.config import settings


def normalize_minio_endpoint(endpoint: str) -> str:
    value = (endpoint or "").strip()
    for prefix in ("https://", "http://"):
        if value.lower().startswith(prefix):
            value = value[len(prefix) :]
    return value.split("/")[0].rstrip("/")


class MinioStorage:
    def __init__(self):
        self.client = Minio(
            normalize_minio_endpoint(settings.minio_endpoint),
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_secure,
        )
        self.bucket = settings.minio_bucket
        self._ensure_bucket()

    def _ensure_bucket(self):
        if not self.client.bucket_exists(self.bucket):
            self.client.make_bucket(self.bucket)

    def download_prefix(self, object_key_prefix: str, target_dir: Path) -> list[Path]:
        target_dir.mkdir(parents=True, exist_ok=True)
        prefix = object_key_prefix if object_key_prefix.endswith("/") else object_key_prefix + "/"
        downloaded: list[Path] = []
        for obj in self.client.list_objects(self.bucket, prefix=prefix, recursive=True):
            if obj.is_dir:
                continue
            rel = obj.object_name[len(prefix) :] if obj.object_name.startswith(prefix) else obj.object_name
            local_path = target_dir / rel
            local_path.parent.mkdir(parents=True, exist_ok=True)
            self.client.fget_object(self.bucket, obj.object_name, str(local_path))
            downloaded.append(local_path)
        if not downloaded:
            raise FileNotFoundError(f"MinIO 前缀下无对象: {self.bucket}/{prefix}")
        return downloaded

    def upload_file(self, object_key: str, local_path: Path, content_type: str = "application/octet-stream"):
        self.client.fput_object(self.bucket, object_key, str(local_path), content_type=content_type)

    def remove_dir(self, path: Path):
        if path.exists():
            shutil.rmtree(path, ignore_errors=True)
