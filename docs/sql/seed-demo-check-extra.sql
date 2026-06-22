-- Extra PACS demo rows: status=20 (paid, pending execution)
-- Run: psql -U postgres -d hospital -f docs/sql/seed-demo-check-extra.sql

\encoding UTF8

BEGIN;

-- Reset primary demo IDs back to paid queue
UPDATE check_request
SET status = 20,
    executor_id = NULL,
    execute_time = NULL,
    result_text = NULL,
    result_input_id = NULL,
    result_time = NULL,
    delmark = 0
WHERE id IN (62001, 62002);

-- #62003 Zhao daye - head CT
INSERT INTO check_request (
    id, register_id, patient_id, medical_technology_id, doctor_id,
    item_price, purpose, body_part, status, order_time
)
SELECT
    62003,
    (SELECT r.id FROM register r JOIN patient p ON r.patient_id = p.id
     WHERE p.medical_record_no = 'MR202606040003' AND r.delmark = 0
     ORDER BY r.id DESC LIMIT 1),
    p.id, mt.id, 1,
    280.00, 'head CT follow-up', 'head', 20, NOW()
FROM patient p
JOIN medical_technology mt ON mt.item_code = 'CHK-CT-HEAD'
WHERE p.medical_record_no = 'MR202606040003'
ON CONFLICT (id) DO UPDATE
SET status = 20, delmark = 0, purpose = EXCLUDED.purpose, body_part = EXCLUDED.body_part,
    register_id = EXCLUDED.register_id, patient_id = EXCLUDED.patient_id,
    medical_technology_id = EXCLUDED.medical_technology_id,
    executor_id = NULL, execute_time = NULL, result_text = NULL;

-- #62004 Zhao daye - chest CT
INSERT INTO check_request (
    id, register_id, patient_id, medical_technology_id, doctor_id,
    item_price, purpose, body_part, status, order_time
)
SELECT
    62004,
    (SELECT r.id FROM register r JOIN patient p ON r.patient_id = p.id
     WHERE p.medical_record_no = 'MR202606040003' AND r.delmark = 0
     ORDER BY r.id DESC LIMIT 1),
    p.id, mt.id, 1,
    320.00, 'chest CT screening', 'chest', 20, NOW()
FROM patient p
JOIN medical_technology mt ON mt.item_code = 'CHK-CT-LUNG'
WHERE p.medical_record_no = 'MR202606040003'
ON CONFLICT (id) DO UPDATE
SET status = 20, delmark = 0, purpose = EXCLUDED.purpose, body_part = EXCLUDED.body_part,
    register_id = EXCLUDED.register_id, patient_id = EXCLUDED.patient_id,
    medical_technology_id = EXCLUDED.medical_technology_id,
    executor_id = NULL, execute_time = NULL, result_text = NULL;

-- #62005 CNN-Demo - head CT
INSERT INTO check_request (
    id, register_id, patient_id, medical_technology_id, doctor_id,
    item_price, purpose, body_part, status, order_time
)
SELECT
    62005,
    r.id, p.id, mt.id, 1,
    280.00, 'head CT demo', 'head', 20, NOW()
FROM patient p
JOIN register r ON r.patient_id = p.id AND r.delmark = 0
JOIN medical_technology mt ON mt.item_code = 'CHK-CT-HEAD'
WHERE p.medical_record_no = 'MR202606119518'
ORDER BY r.id DESC
LIMIT 1
ON CONFLICT (id) DO UPDATE
SET status = 20, delmark = 0, purpose = EXCLUDED.purpose, body_part = EXCLUDED.body_part,
    register_id = EXCLUDED.register_id, patient_id = EXCLUDED.patient_id,
    medical_technology_id = EXCLUDED.medical_technology_id,
    executor_id = NULL, execute_time = NULL, result_text = NULL;

-- #62006 pacs-patient - tumor CT
INSERT INTO check_request (
    id, register_id, patient_id, medical_technology_id, doctor_id,
    item_price, purpose, body_part, status, order_time
)
SELECT
    62006,
    r.id, p.id, mt.id, 1,
    450.00, 'tumor segmentation', 'chest', 20, NOW()
FROM patient p
JOIN register r ON r.patient_id = p.id AND r.delmark = 0
JOIN medical_technology mt ON mt.item_code = 'CHK-TUMOR-SEG'
WHERE p.medical_record_no = 'MR202606110867'
ORDER BY r.id DESC
LIMIT 1
ON CONFLICT (id) DO UPDATE
SET status = 20, delmark = 0, purpose = EXCLUDED.purpose, body_part = EXCLUDED.body_part,
    register_id = EXCLUDED.register_id, patient_id = EXCLUDED.patient_id,
    medical_technology_id = EXCLUDED.medical_technology_id,
    executor_id = NULL, execute_time = NULL, result_text = NULL;

SELECT setval('check_request_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM check_request), 62006));

COMMIT;
