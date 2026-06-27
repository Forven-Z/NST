-- 药房驳回发药：prescription 增补驳回原因、药师、时间
-- 执行前请确认已存在 prescription、employee 表

ALTER TABLE prescription
    ADD COLUMN IF NOT EXISTS reject_reason         VARCHAR(256),
    ADD COLUMN IF NOT EXISTS reject_pharmacist_id  BIGINT REFERENCES employee(id),
    ADD COLUMN IF NOT EXISTS reject_time           TIMESTAMPTZ;
