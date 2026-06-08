-- 患者手机号：非空时全院唯一（儿童等可 NULL）
-- 已有库增量执行；新库请直接用 schema.sql

DROP INDEX IF EXISTS ix_patient_phone;
CREATE UNIQUE INDEX IF NOT EXISTS ux_patient_phone ON patient(phone)
    WHERE delmark = 0 AND phone IS NOT NULL AND phone <> '';
