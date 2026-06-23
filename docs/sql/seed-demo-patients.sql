-- 演示患者 + 今日内科挂号（小程序/窗口联调）
-- 执行：psql -U postgres -d hospital -f docs/sql/seed-demo-patients.sql
-- 依赖：已跑 seed-dict.sql（科室、员工、排班）

\encoding UTF8

BEGIN;

-- 主就诊人（与小程序 Mock profile 对齐，便于真库登录）
INSERT INTO patient (
    medical_record_no, real_name, gender, birth_date, age, id_card, phone,
    address, settle_category_id
)
VALUES (
    'MR202606040100', '测试患者', 1, '1990-01-01', 36,
    '110101199001011234', '13800138001', '', 1
)
ON CONFLICT (medical_record_no) DO UPDATE
SET real_name = EXCLUDED.real_name,
    gender = EXCLUDED.gender,
    birth_date = EXCLUDED.birth_date,
    age = EXCLUDED.age,
    id_card = EXCLUDED.id_card,
    phone = EXCLUDED.phone,
    delmark = 0;

-- 今日内科普通号 · 已挂号（visit_state=1），doctor01 队列可见
INSERT INTO register (
    patient_id, dept_id, employee_id, regist_level_id, settle_category_id,
    visit_date, noon_type, visit_state, channel, regist_fee
)
SELECT p.id, 1, 1, 1, 1, CURRENT_DATE, 1, 1, 'MINIAPP', 20.00
FROM patient p
WHERE p.medical_record_no = 'MR202606040100'
  AND NOT EXISTS (
      SELECT 1 FROM register r
      JOIN patient p2 ON r.patient_id = p2.id
      WHERE p2.medical_record_no = 'MR202606040100'
        AND r.visit_date = CURRENT_DATE
        AND r.dept_id = 1
        AND r.delmark = 0
  );

SELECT setval('patient_id_seq', (SELECT COALESCE(MAX(id), 1) FROM patient));
SELECT setval('register_id_seq', (SELECT COALESCE(MAX(id), 1) FROM register));

COMMIT;
