-- 无身份证号患儿：在 patient_family_link 记录陪诊人（监护人）快照
-- 执行前请确认已存在 patient_family_link 表

ALTER TABLE patient_family_link
    ADD COLUMN IF NOT EXISTS no_id_card BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS guardian_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS guardian_id_card VARCHAR(18),
    ADD COLUMN IF NOT EXISTS guardian_phone VARCHAR(20);

COMMENT ON COLUMN patient_family_link.no_id_card IS '无身份证号患儿；true 时 member 的 patient.id_card 为空';
COMMENT ON COLUMN patient_family_link.guardian_name IS '陪诊人/监护人姓名（无身份证患儿必填）';
COMMENT ON COLUMN patient_family_link.guardian_id_card IS '陪诊人身份证号（须与账号持有人一致）';
COMMENT ON COLUMN patient_family_link.guardian_phone IS '陪诊人联系电话';
