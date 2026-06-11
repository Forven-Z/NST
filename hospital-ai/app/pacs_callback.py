import httpx

from app.config import settings


def post_callback(payload: dict, callback_url: str | None = None) -> None:
    url = callback_url or settings.pacs_callback_url
    with httpx.Client(timeout=30.0) as client:
        resp = client.post(url, json=payload)
        resp.raise_for_status()
