-- 检验结果明细表（已有库增量执行）
-- psql -U postgres -d hospital -f docs/sql/migration-inspection-result-item.sql

CREATE TABLE IF NOT EXISTS inspection_result_item (
    id                      BIGSERIAL PRIMARY KEY,
    inspection_request_id   BIGINT       NOT NULL REFERENCES inspection_request(id) ON DELETE CASCADE,
    sort_order              SMALLINT     NOT NULL DEFAULT 0,
    item_code               VARCHAR(32),
    item_name               VARCHAR(128) NOT NULL,
    result_value            VARCHAR(64)  NOT NULL,
    unit                    VARCHAR(32),
    ref_range               VARCHAR(64),
    abnormal_flag           VARCHAR(8),
    create_time             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_inspection_result_item_request
    ON inspection_result_item(inspection_request_id);

COMMENT ON COLUMN inspection_result_item.abnormal_flag IS 'H偏高 L偏低 N正常，空=未判定';
