-- 医技报告审核人（支持单签：reviewer_id = result_input_id；双签：分两次登录签阅）
ALTER TABLE inspection_request ADD COLUMN IF NOT EXISTS reviewer_id BIGINT REFERENCES employee(id);
ALTER TABLE check_request ADD COLUMN IF NOT EXISTS reviewer_id BIGINT REFERENCES employee(id);
ALTER TABLE disposal_request ADD COLUMN IF NOT EXISTS reviewer_id BIGINT REFERENCES employee(id);

COMMENT ON COLUMN inspection_request.reviewer_id IS '检验报告审核人；空=待审核';
COMMENT ON COLUMN check_request.reviewer_id IS '检查报告审核人；空=待审核';
COMMENT ON COLUMN disposal_request.reviewer_id IS '处置记录审核人；空=待审核';
