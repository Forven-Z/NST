-- 旧库：relation_type 去掉 6，默认改为 4
UPDATE patient_family_link SET relation_type = 4 WHERE relation_type = 6;
ALTER TABLE patient_family_link ALTER COLUMN relation_type SET DEFAULT 4;
COMMENT ON COLUMN patient_family_link.relation_type IS '0本人 1父母 2配偶 3子女 4其他';
