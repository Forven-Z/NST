-- 家属就诊人（代挂号）— 参考开源 hospital 就诊卡模型
-- 执行：psql -U postgres -d hospital -f docs/sql/patch-family-link.sql

BEGIN;

CREATE TABLE IF NOT EXISTS patient_family_link (
    id                  BIGSERIAL PRIMARY KEY,
    owner_patient_id    BIGINT       NOT NULL REFERENCES patient(id),
    member_patient_id   BIGINT       NOT NULL REFERENCES patient(id),
    relation_type       SMALLINT     NOT NULL DEFAULT 6,
    delmark             SMALLINT     NOT NULL DEFAULT 0,
    create_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_time         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner_patient_id, member_patient_id)
);
CREATE INDEX IF NOT EXISTS ix_patient_family_owner ON patient_family_link(owner_patient_id);

COMMENT ON COLUMN patient_family_link.relation_type IS '0本人 1父母 2配偶 3子女 4其他';

COMMIT;
