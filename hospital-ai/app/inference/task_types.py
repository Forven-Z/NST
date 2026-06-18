"""CNN 任务类型常量（与 pacs / imaging_study.modality 对齐）。"""

HEAD_CT_ARTIFACT = "HEAD_CT_ARTIFACT"
LUNG_CT_ARTIFACT = "LUNG_CT_ARTIFACT"
TUMOR_SEG = "TUMOR_SEG"

DEFAULT_TASK = HEAD_CT_ARTIFACT

SUPPORTED_TASKS = (HEAD_CT_ARTIFACT, LUNG_CT_ARTIFACT, TUMOR_SEG)


def normalize_task_type(raw: str | None) -> str:
    if not raw:
        return DEFAULT_TASK
    value = raw.strip().upper()
    if value in SUPPORTED_TASKS:
        return value
    return DEFAULT_TASK
