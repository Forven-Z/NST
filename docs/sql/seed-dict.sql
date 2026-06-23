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
    (5, 'REGISTRATION', '挂号收费处', 4, 5),
    (6, 'SURGERY', '外科', 1, 6),
    (7, 'DISPOSAL', '处置科', 2, 7),
    (8, 'INFO_CENTER', '信息科', 4, 8)
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
    (6, 'E006', '系统管理员', 1, 8, '管理员', 'ADMIN')
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

-- 门诊排班：见文件末尾扩展段（各科室每半天 1 名普通医生轮流；专家每周 2 个半天）
-- 疾病字典（示例）
INSERT INTO disease (disease_code, disease_name, disease_category) VALUES
    ('J06.9', '急性上呼吸道感染', '呼吸系统'),
    ('I10', '原发性高血压', '循环系统')
ON CONFLICT (disease_code) DO NOTHING;

-- 医技项目（P2/P3 联调预置）
INSERT INTO medical_technology (item_code, item_name, tech_type, price, dept_id) VALUES
    ('CHK-CT-HEAD', '头部 CT', 'CHECK', 280.00, 2),
    ('CHK-CT-LUNG', '胸部 CT', 'CHECK', 320.00, 2),
    ('CHK-TUMOR-SEG', '肿瘤 CT 分割', 'CHECK', 450.00, 2),
    ('INS-BLOOD', '血常规', 'INSPECTION', 35.00, 3),
    ('DIS-WASH', '洗胃', 'DISPOSAL', 120.00, 7),
    ('DIS-INF', '静脉输液', 'DISPOSAL', 45.00, 7)
ON CONFLICT (item_code) DO NOTHING;

-- 药品（P3 预置）
INSERT INTO drug_info (drug_code, drug_name, drug_format, drug_dosage, drug_type, unit, retail_price, stock_qty) VALUES
    ('DRG-001', '阿莫西林胶囊', '0.25g×24粒', '胶囊', '处方药', '盒', 18.50, 100),
    ('DRG-002', '布洛芬缓释胶囊', '0.3g×20粒', '胶囊', '处方药', '盒', 22.00, 80)
ON CONFLICT (drug_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 扩展：门诊医生 doctor02～doctor05；处置 disposal01（密码均为 123456）
-- 科室 6/7 已并入上文 department；不修改上文员工/账号原 6 行
-- ---------------------------------------------------------------------------

SELECT setval('department_id_seq', (SELECT COALESCE(MAX(id), 1) FROM department));

INSERT INTO employee (id, emp_no, real_name, gender, dept_id, title, role_type) VALUES
    (7,  'E007', '李医生', 1, 1, '主治医师', 'OUTPATIENT_DOCTOR'),
    (8,  'E008', '陈教授', 1, 1, '主任医师', 'OUTPATIENT_DOCTOR'),
    (9,  'E009', '王医生', 1, 6, '主治医师', 'OUTPATIENT_DOCTOR'),
    (10, 'E010', '刘教授', 1, 6, '主任医师', 'OUTPATIENT_DOCTOR'),
    (11, 'E011', '孙处置', 1, 7, '处置医师', 'DISPOSAL_DOCTOR'),
    (12, 'E012', '赵医生', 1, 6, '主治医师', 'OUTPATIENT_DOCTOR')
ON CONFLICT (emp_no) DO NOTHING;

SELECT setval('employee_id_seq', (SELECT COALESCE(MAX(id), 1) FROM employee));

INSERT INTO sys_user (id, username, password_hash, employee_id, user_type, status) VALUES
    (7,  'doctor02',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 7,  'STAFF', 1),
    (8,  'doctor03',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 8,  'STAFF', 1),
    (9,  'doctor04',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 9,  'STAFF', 1),
    (10, 'doctor05',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 10, 'STAFF', 1),
    (11, 'disposal01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 11, 'STAFF', 1),
    (12, 'doctor06',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 12, 'STAFF', 1)
ON CONFLICT (username) DO NOTHING;

SELECT setval('sys_user_id_seq', (SELECT COALESCE(MAX(id), 1) FROM sys_user));

-- 排班（未来 7 天）：每日上/下午开诊（含周日）；同一半天可有多名普通医生同时出诊
-- 内科：张医生休周日、李医生休周三，其余日期两人同时在岗（各上 6 天/周）
-- 外科：王医生休周日、赵医生休周三，其余日期两人同时在岗（各上 6 天/周）
-- 专家：陈/刘教授各每周 2 个上午
UPDATE scheduling
SET publish_status = 2
WHERE publish_status = 1
  AND work_date BETWEEN CURRENT_DATE AND (CURRENT_DATE + 6)
  AND employee_id IN (1, 7, 8, 9, 10, 12);

INSERT INTO scheduling (employee_id, regist_level_id, work_date, noon_type, total_quota, used_quota, publish_status)
SELECT doc.employee_id,
       1,
       (CURRENT_DATE + d)::date,
       n.noon_type,
       30,
       0,
       1
FROM generate_series(0, 6) AS d
CROSS JOIN (VALUES (1), (2)) AS n(noon_type)
CROSS JOIN (VALUES (1), (7), (9), (12)) AS doc(employee_id)
WHERE (
    (doc.employee_id = 1 AND extract(isodow FROM (CURRENT_DATE + d)::date) <> 7)
    OR (doc.employee_id = 7 AND extract(isodow FROM (CURRENT_DATE + d)::date) <> 3)
    OR (doc.employee_id = 9 AND extract(isodow FROM (CURRENT_DATE + d)::date) <> 7)
    OR (doc.employee_id = 12 AND extract(isodow FROM (CURRENT_DATE + d)::date) <> 3)
)
  AND NOT EXISTS (
      SELECT 1 FROM scheduling s
      WHERE s.employee_id = doc.employee_id
        AND s.regist_level_id = 1
        AND s.work_date = (CURRENT_DATE + d)::date
        AND s.noon_type = n.noon_type
        AND s.publish_status <> 2
  );

INSERT INTO scheduling (employee_id, regist_level_id, work_date, noon_type, total_quota, used_quota, publish_status)
SELECT e.employee_id,
       2,
       (CURRENT_DATE + d)::date,
       1,
       12,
       0,
       1
FROM generate_series(0, 6) AS d
CROSS JOIN (
    VALUES
        (8,  ARRAY[1, 4]),   -- 陈教授 内科：周一、周四上午
        (10, ARRAY[2, 5])    -- 刘教授 外科：周二、周五上午
) AS e(employee_id, expert_days)
WHERE extract(isodow FROM (CURRENT_DATE + d)::date) = ANY (e.expert_days)
  AND NOT EXISTS (
      SELECT 1 FROM scheduling s
      WHERE s.employee_id = e.employee_id
        AND s.regist_level_id = 2
        AND s.work_date = (CURRENT_DATE + d)::date
        AND s.noon_type = 1
        AND s.publish_status <> 2
  );
-- 旧库若已写入 DIS-WASH 挂内科，纠正为处置科
UPDATE medical_technology SET dept_id = 7 WHERE item_code = 'DIS-WASH' AND dept_id <> 7;

-- 医技账号：检验 lab02；检查 check02/check03（密码均为 123456）
INSERT INTO employee (id, emp_no, real_name, gender, dept_id, title, role_type) VALUES
    (13, 'E013', '周检验', 2, 3, '检验师', 'LAB_DOCTOR'),
    (15, 'E015', '李影像', 1, 2, '影像医师', 'CHECK_DOCTOR'),
    (16, 'E016', '陈影像', 1, 2, '影像医师', 'CHECK_DOCTOR')
ON CONFLICT (emp_no) DO NOTHING;

UPDATE employee SET delmark = 1 WHERE emp_no = 'E014';

-- 旧库：释放 emp13/emp14 上的废弃登录名（employee_id UNIQUE）
UPDATE sys_user
SET username = '_deprecated_inspection01',
    delmark = 1,
    status = 0,
    employee_id = NULL
WHERE username = 'inspection01';

UPDATE sys_user
SET username = '_deprecated_lab02_emp14',
    delmark = 1,
    status = 0,
    employee_id = NULL
WHERE username = 'lab02' AND employee_id <> 13;

SELECT setval('employee_id_seq', (SELECT COALESCE(MAX(id), 1) FROM employee));

INSERT INTO sys_user (username, password_hash, employee_id, user_type, status) VALUES
    ('lab02',   '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 13, 'STAFF', 1),
    ('check02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 15, 'STAFF', 1),
    ('check03', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 16, 'STAFF', 1)
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    employee_id = EXCLUDED.employee_id,
    user_type = EXCLUDED.user_type,
    status = 1,
    delmark = 0;

UPDATE employee SET dept_id = 8 WHERE emp_no = 'E006' AND dept_id <> 8;

SELECT setval('sys_user_id_seq', (SELECT COALESCE(MAX(id), 1) FROM sys_user));

COMMIT;
