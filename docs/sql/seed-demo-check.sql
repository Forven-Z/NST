-- CNN 影像 AI 工作台演示：赵大爷 · 头部 CT · 已缴费（status=20）
-- 执行：psql -U postgres -d hospital -f docs/sql/seed-demo-check.sql
-- 与 Mock 队列中的 checkRequestId=62001 对齐，便于联调

\encoding UTF8

BEGIN;

-- 患者（按病历号幂等）
INSERT INTO patient (medical_record_no, real_name, gender, age, phone, settle_category_id)
VALUES ('MR202606040003', '赵大爷', 1, 67, '13800006003', 1)
ON CONFLICT (medical_record_no) DO UPDATE
SET real_name = EXCLUDED.real_name, gender = EXCLUDED.gender, age = EXCLUDED.age, delmark = 0;

-- 挂号（今日内科复诊，同一患者仅保留一条演示挂号）
INSERT INTO register (
    patient_id, dept_id, employee_id, regist_level_id, settle_category_id,
    visit_date, noon_type, visit_state, channel, regist_fee
)
SELECT p.id, 1, 1, 1, 1, CURRENT_DATE, 1, 2, 'WINDOW', 20.00
FROM patient p
WHERE p.medical_record_no = 'MR202606040003'
  AND NOT EXISTS (
      SELECT 1 FROM register r
      JOIN patient p2 ON r.patient_id = p2.id
      WHERE p2.medical_record_no = 'MR202606040003'
        AND r.visit_date = CURRENT_DATE
        AND r.delmark = 0
  );

-- 检查申请 #62001（已缴费，放射科队列可见）
INSERT INTO check_request (
    id, register_id, patient_id, medical_technology_id, doctor_id,
    item_price, purpose, body_part, status, order_time
)
SELECT
    62001,
    (SELECT r.id FROM register r
     JOIN patient p ON r.patient_id = p.id
     WHERE p.medical_record_no = 'MR202606040003' AND r.delmark = 0
     ORDER BY r.id DESC LIMIT 1),
    p.id,
    mt.id,
    1,
    280.00, '头部 CT 复查', 'head', 20, NOW()
FROM patient p
JOIN medical_technology mt ON mt.item_code = 'CHK-CT-HEAD'
WHERE p.medical_record_no = 'MR202606040003'
ON CONFLICT (id) DO UPDATE
SET status = 20, delmark = 0, body_part = 'head', purpose = '头部 CT 复查',
    register_id = EXCLUDED.register_id, patient_id = EXCLUDED.patient_id;

-- 同步序列，避免后续自增 ID 冲突
SELECT setval('patient_id_seq', (SELECT COALESCE(MAX(id), 1) FROM patient));
SELECT setval('register_id_seq', (SELECT COALESCE(MAX(id), 1) FROM register));
SELECT setval('check_request_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM check_request), 62002));

-- 检查申请 #62002（肺部 CT 演示，用于 taskType 自测）
INSERT INTO check_request (
    id, register_id, patient_id, medical_technology_id, doctor_id,
    item_price, purpose, body_part, status, order_time
)
SELECT
    62002,
    (SELECT r.id FROM register r
     JOIN patient p ON r.patient_id = p.id
     WHERE p.medical_record_no = 'MR202606040003' AND r.delmark = 0
     ORDER BY r.id DESC LIMIT 1),
    p.id,
    mt.id,
    1,
    320.00, '肺部 CT 筛查', 'chest', 20, NOW()
FROM patient p
JOIN medical_technology mt ON mt.item_code = 'CHK-CT-LUNG'
WHERE p.medical_record_no = 'MR202606040003'
ON CONFLICT (id) DO UPDATE
SET status = 20, delmark = 0, body_part = 'chest', purpose = '肺部 CT 筛查',
    register_id = EXCLUDED.register_id, patient_id = EXCLUDED.patient_id,
    medical_technology_id = EXCLUDED.medical_technology_id;

COMMIT;
