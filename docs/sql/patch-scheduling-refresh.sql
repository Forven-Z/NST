-- 刷新未来 7 天排班（联调前若号源为空可执行）
-- 兼容旧库 scheduling.dept_id 列；新库 schema.sql 无 dept_id 时用 seed-dict.sql 即可

INSERT INTO scheduling (dept_id, employee_id, regist_level_id, work_date, noon_type, total_quota, used_quota, publish_status)
SELECT 1, 1, 1, (CURRENT_DATE + d)::date, n.noon_type, 30, 0, 1
FROM generate_series(0, 6) AS d
CROSS JOIN (VALUES (1), (2)) AS n(noon_type)
WHERE NOT EXISTS (
    SELECT 1 FROM scheduling s
    WHERE s.employee_id = 1
      AND s.regist_level_id = 1
      AND s.work_date = (CURRENT_DATE + d)::date
      AND s.noon_type = n.noon_type
      AND COALESCE(s.delmark, 0) = 0
);
