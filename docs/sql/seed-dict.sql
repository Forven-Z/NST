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
-- 疾病字典（演示 + RAG 常见诊断）
INSERT INTO disease (disease_code, disease_name, disease_category) VALUES
    ('J06.9', '急性上呼吸道感染', '呼吸系统'),
    ('I10', '原发性高血压', '循环系统'),
    ('R51', '头痛', '神经系统'),
    ('R50.9', '发热', '症状体征'),
    ('E11.9', '2型糖尿病', '内分泌')
ON CONFLICT (disease_code) DO UPDATE
SET disease_name = EXCLUDED.disease_name,
    disease_category = EXCLUDED.disease_category,
    delmark = 0;

-- 医技项目：对齐 RAG TECHNOLOGY_GUIDE / DISPOSAL_GUIDE 与 DEMO_MEDICAL_RECORD_SAMPLES
INSERT INTO medical_technology (item_code, item_name, tech_type, price, dept_id) VALUES
    -- 检验 INSPECTION（检验科 dept_id=3）
    ('INS-BLOOD', '血常规', 'INSPECTION', 35.00, 3),
    ('INS-CRP', 'C反应蛋白', 'INSPECTION', 45.00, 3),
    ('INS-PCT', '降钙素原', 'INSPECTION', 80.00, 3),
    ('INS-URINE', '尿常规', 'INSPECTION', 25.00, 3),
    ('INS-STOOL', '粪便常规及隐血', 'INSPECTION', 30.00, 3),
    ('INS-LIVER', '肝功能', 'INSPECTION', 55.00, 3),
    ('INS-KIDNEY', '肾功能', 'INSPECTION', 50.00, 3),
    ('INS-GLU', '空腹血糖', 'INSPECTION', 12.00, 3),
    ('INS-HBA1C', '糖化血红蛋白', 'INSPECTION', 60.00, 3),
    ('INS-LIPID', '血脂四项', 'INSPECTION', 70.00, 3),
    ('INS-ELECTROLYTE', '电解质', 'INSPECTION', 40.00, 3),
    ('INS-COAG', '凝血功能', 'INSPECTION', 65.00, 3),
    ('INS-THYROID', '甲状腺功能', 'INSPECTION', 90.00, 3),
    ('INS-CARDIAC', '心肌标志物', 'INSPECTION', 120.00, 3),
    ('INS-RESP-AG', '呼吸道病原抗原', 'INSPECTION', 85.00, 3),
    -- 检查 CHECK（放射科 dept_id=2）
    ('CHK-CT-HEAD', '头部 CT', 'CHECK', 280.00, 2),
    ('CHK-CT-LUNG', '胸部 CT', 'CHECK', 320.00, 2),
    ('CHK-TUMOR-SEG', '肿瘤 CT 分割', 'CHECK', 450.00, 2),
    ('CHK-CXR', '胸部 X 线', 'CHECK', 90.00, 2),
    ('CHK-MRI-BRAIN', '颅脑 MRI', 'CHECK', 680.00, 2),
    ('CHK-CT-ABD', '腹部 CT', 'CHECK', 350.00, 2),
    ('CHK-US-ABD', '腹部超声', 'CHECK', 120.00, 2),
    ('CHK-US-THYROID', '甲状腺超声', 'CHECK', 100.00, 2),
    ('CHK-US-URINARY', '泌尿系统超声', 'CHECK', 110.00, 2),
    ('CHK-ECG', '十二导联心电图', 'CHECK', 30.00, 2),
    ('CHK-ECHO', '超声心动图', 'CHECK', 180.00, 2),
    ('CHK-HOLTER', '动态心电图', 'CHECK', 200.00, 2),
    ('CHK-PFT', '肺功能检查', 'CHECK', 150.00, 2),
    -- 处置 DISPOSAL（处置科 dept_id=7）
    ('DIS-INF', '静脉输液', 'DISPOSAL', 45.00, 7),
    ('DIS-WASH', '洗胃', 'DISPOSAL', 120.00, 7),
    ('DIS-DRESSING', '清创换药', 'DISPOSAL', 80.00, 7),
    ('DIS-NEB', '雾化吸入', 'DISPOSAL', 35.00, 7),
    ('DIS-O2', '氧疗', 'DISPOSAL', 50.00, 7),
    ('DIS-CATH', '导尿', 'DISPOSAL', 40.00, 7)
ON CONFLICT (item_code) DO UPDATE
SET item_name = EXCLUDED.item_name,
    tech_type = EXCLUDED.tech_type,
    price = EXCLUDED.price,
    dept_id = EXCLUDED.dept_id,
    delmark = 0;

-- 药品：对齐 RAG DRUG_INSTRUCTION DRUG-001～020（演示 AI 处方草稿候选）
INSERT INTO drug_info (drug_code, drug_name, drug_format, drug_dosage, drug_type, unit, retail_price, stock_qty) VALUES
    ('DRG-001', '阿莫西林胶囊', '0.25g×24粒', '胶囊', '处方药', '盒', 18.50, 100),
    ('DRG-002', '布洛芬缓释胶囊', '0.3g×20粒', '胶囊', '处方药', '盒', 22.00, 80),
    ('DRG-003', '对乙酰氨基酚片', '0.5g×20片', '片剂', '处方药', '盒', 8.50, 200),
    ('DRG-004', '氯雷他定片', '10mg×6片', '片剂', '处方药', '盒', 16.00, 120),
    ('DRG-005', '盐酸西替利嗪片', '10mg×12片', '片剂', '处方药', '盒', 14.50, 100),
    ('DRG-006', '盐酸氨溴索片', '30mg×20片', '片剂', '处方药', '盒', 19.00, 90),
    ('DRG-007', '乙酰半胱氨酸颗粒', '0.1g×10袋', '颗粒', '处方药', '盒', 28.00, 60),
    ('DRG-008', '奥美拉唑肠溶胶囊', '20mg×14粒', '胶囊', '处方药', '盒', 25.00, 80),
    ('DRG-009', '蒙脱石散', '3g×10袋', '散剂', 'OTC', '盒', 12.00, 150),
    ('DRG-010', '口服补液盐散', '20.5g×3袋', '散剂', 'OTC', '盒', 15.00, 100),
    ('DRG-011', '二甲双胍片', '0.5g×48片', '片剂', '处方药', '盒', 11.00, 120),
    ('DRG-012', '苯磺酸氨氯地平片', '5mg×7片', '片剂', '处方药', '盒', 18.00, 100),
    ('DRG-013', '氯沙坦钾片', '50mg×7片', '片剂', '处方药', '盒', 32.00, 80),
    ('DRG-014', '阿托伐他汀钙片', '20mg×7片', '片剂', '处方药', '盒', 38.00, 70),
    ('DRG-015', '硫酸沙丁胺醇吸入气雾剂', '100μg×200揿', '气雾剂', '处方药', '瓶', 42.00, 50),
    ('DRG-016', '吸入用布地奈德混悬液', '1mg×5支', '混悬液', '处方药', '盒', 68.00, 40),
    ('DRG-017', '阿奇霉素片', '0.25g×6片', '片剂', '处方药', '盒', 24.00, 90),
    ('DRG-018', '头孢呋辛酯片', '0.25g×12片', '片剂', '处方药', '盒', 26.50, 85),
    ('DRG-019', '莫匹罗星软膏', '2% 5g', '软膏', 'OTC', '支', 22.00, 60),
    ('DRG-020', '复方氨酚烷胺片', '12片', '片剂', 'OTC', '盒', 9.50, 180)
ON CONFLICT (drug_code) DO UPDATE
SET drug_name = EXCLUDED.drug_name,
    drug_format = EXCLUDED.drug_format,
    drug_dosage = EXCLUDED.drug_dosage,
    drug_type = EXCLUDED.drug_type,
    unit = EXCLUDED.unit,
    retail_price = EXCLUDED.retail_price,
    stock_qty = EXCLUDED.stock_qty,
    delmark = 0;

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
