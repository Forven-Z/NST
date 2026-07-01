-- 【新环境】clinical_sync_task 已并入 docs/sql/schema.sql（DATABASE_DESIGN v1.16），无需再执行本文件。
-- 【旧库增量】若库在 ADR-019 Outbox 落地前已建，可单独执行本 patch：
-- Windows GBK/UTF8 错误：见 docs/sql/README.md §一（本文件已含 \encoding UTF8）

\encoding UTF8

CREATE TABLE IF NOT EXISTS clinical_sync_task (
    id              BIGSERIAL PRIMARY KEY,
    biz_type        VARCHAR(32)  NOT NULL,
    biz_id          BIGINT       NOT NULL,
    action          VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 10,
    next_retry_at   TIMESTAMPTZ,
    last_error      TEXT,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_clinical_sync_task UNIQUE (biz_type, biz_id, action)
);

CREATE INDEX IF NOT EXISTS idx_clinical_sync_task_status_retry
    ON clinical_sync_task (status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

COMMENT ON TABLE clinical_sync_task IS 'patient 支付/退费后同步 clinical 医嘱 status；失败可重试';
