from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT_DIR = Path(__file__).resolve().parents[1]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=ROOT_DIR / ".env", extra="ignore")

    model_weight_path: str = str(ROOT_DIR / "model" / "weights" / "best.pth")
    lung_model_weight_path: str = str(ROOT_DIR / "model" / "weights" / "lung_artifact_best.pth")
    tumor_model_weight_path: str = str(ROOT_DIR / "model" / "weights" / "tumor_seg_best.pth")
    minio_endpoint: str = "127.0.0.1:9001"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin123"
    minio_secure: bool = False
    minio_bucket: str = "imaging"
    pacs_callback_url: str = "http://127.0.0.1:9104/internal/imaging/callback"
    host: str = "0.0.0.0"
    port: int = 8000
    job_timeout_seconds: int = 600


settings = Settings()
