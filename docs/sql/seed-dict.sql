-- 智慧云脑诊疗平台 — P1 字典与测试数据
-- 执行：psql -U postgres -d hospital -f docs/sql/seed-dict.sql
-- Windows 若报 GBK/UTF8 编码错误，见 docs/sql/README.md §一
-- 密码均为 123456（BCrypt），仅开发环境

\encoding UTF8

BEGIN;

-- 结算类别
INSERT INTO settle_category (id, category_code, category_name) VALUES
    (1, 'SELF_PAY', '自费')
ON CONFLICT (category_code) DO NOTHING;

-- 号别
INSERT INTO regist_level (id, level_code, level_name, regist_fee) VALUES
    (1, 'NORMAL', '普通号', 20.00),
    (2, 'EXPERT', '专家号', 65.00)
ON CONFLICT (level_code) DO NOTHING;

-- 科室
INSERT INTO department (id, dept_code, dept_name, dept_type, sort_no) VALUES
    (1, 'INTERNAL', '内科', 1, 1),
    (2, 'RADIOLOGY', '放射科', 2, 2),
    (3, 'LAB', '检验科', 2, 3),
    (4, 'PHARMACY', '药房', 3, 4),
    (5, 'REGISTRATION', '挂号收费处', 4, 5)
ON CONFLICT (dept_code) DO NOTHING;

SELECT setval('department_id_seq', (SELECT COALESCE(MAX(id), 1) FROM department));
SELECT setval('regist_level_id_seq', (SELECT COALESCE(MAX(id), 1) FROM regist_level));
SELECT setval('settle_category_id_seq', (SELECT COALESCE(MAX(id), 1) FROM settle_category));

-- 员工（角色见 role_type）
INSERT INTO employee (id, emp_no, real_name, gender, dept_id, title, role_type) VALUES
    (1, 'E001', '张医生', 1, 1, '主治医师', 'OUTPATIENT_DOCTOR'),
    (2, 'E002', '李检验', 2, 3, '检验师', 'LAB_DOCTOR'),
    (3, 'E003', '王检查', 1, 2, '影像医师', 'CHECK_DOCTOR'),
    (4, 'E004', '赵药师', 2, 4, '主管药师', 'PHARMACIST'),
    (5, 'E005', '钱收费', 1, 5, '收费员', 'REGISTRAR'),
    (6, 'E006', '系统管理员', 1, 5, '管理员', 'ADMIN')
ON CONFLICT (emp_no) DO NOTHING;

SELECT setval('employee_id_seq', (SELECT COALESCE(MAX(id), 1) FROM employee));

-- 登录账号（password: 123456，BCrypt）
INSERT INTO sys_user (id, username, password_hash, employee_id, user_type, status) VALUES
    (1, 'doctor01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 1, 'STAFF', 1),
    (2, 'lab01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 2, 'STAFF', 1),
    (3, 'check01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 3, 'STAFF', 1),
    (4, 'pharmacy01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 4, 'STAFF', 1),
    (5, 'registrar01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 5, 'STAFF', 1),
    (6, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 6, 'ADMIN', 1)
ON CONFLICT (username) DO NOTHING;

SELECT setval('sys_user_id_seq', (SELECT COALESCE(MAX(id), 1) FROM sys_user));

-- 排班（未来 7 天内科普通号，已发布）
INSERT INTO scheduling (dept_id, employee_id, regist_level_id, work_date, noon_type, total_quota, used_quota, publish_status)
SELECT
    1, 1, 1,
    (CURRENT_DATE + d)::date,
    n.noon_type,
    30, 0, 1
FROM generate_series(0, 6) AS d
CROSS JOIN (VALUES (1), (2)) AS n(noon_type)
WHERE NOT EXISTS (
    SELECT 1 FROM scheduling s
    WHERE s.dept_id = 1 AND s.employee_id = 1 AND s.regist_level_id = 1
      AND s.work_date = (CURRENT_DATE + d)::date AND s.noon_type = n.noon_type
);

-- 疾病字典（示例）
INSERT INTO disease (disease_code, disease_name, disease_category) VALUES
    ('J06.9', '急性上呼吸道感染', '呼吸系统'),
    ('I10', '原发性高血压', '循环系统')
ON CONFLICT (disease_code) DO NOTHING;

-- 医技项目（P2/P3 联调预置）
INSERT INTO medical_technology (item_code, item_name, tech_type, price, dept_id) VALUES
    ('CHK-CT-HEAD', '头部 CT', 'CHECK', 280.00, 2),
    ('INS-BLOOD', '血常规', 'INSPECTION', 35.00, 3),
    ('DIS-WASH', '洗胃', 'DISPOSAL', 120.00, 1)
ON CONFLICT (item_code) DO NOTHING;

-- 药品（P3 预置）
INSERT INTO drug_info (drug_code, drug_name, specification, unit, retail_price, stock_qty) VALUES
    ('DRG-001', '阿莫西林胶囊', '0.25g*24粒', '盒', 18.50, 100),
    ('DRG-002', '布洛芬缓释胶囊', '0.3g*20粒', '盒', 22.00, 80)
ON CONFLICT (drug_code) DO NOTHING;

COMMIT;
