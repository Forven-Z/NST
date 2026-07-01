package com.hospital.patient.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SchedulingRepository {

    private final JdbcClient jdbcClient;

    public SchedulingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> findAvailableSchedules(Long deptId, LocalDate workDate,
                                                            Integer noonType, Long registLevelId) {
        return jdbcClient.sql("""
                        SELECT s.id AS scheduling_id,
                               e.dept_id,
                               d.dept_name,
                               s.employee_id,
                               e.real_name AS doctor_name,
                               s.regist_level_id,
                               rl.level_name,
                               rl.regist_fee,
                               (s.total_quota - s.used_quota) AS remain_quota
                        FROM scheduling s
                        JOIN employee e ON s.employee_id = e.id
                        JOIN department d ON e.dept_id = d.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.publish_status = 1
                          AND s.work_date >= CURRENT_DATE
                          AND s.work_date = :workDate
                          AND (s.total_quota - s.used_quota) > 0
                          AND e.delmark = 0
                          AND (CAST(:deptId AS BIGINT) IS NULL OR e.dept_id = CAST(:deptId AS BIGINT))
                          AND (CAST(:noonType AS INTEGER) IS NULL OR s.noon_type = CAST(:noonType AS INTEGER))
                          AND (CAST(:registLevelId AS BIGINT) IS NULL OR s.regist_level_id = CAST(:registLevelId AS BIGINT))
                        ORDER BY e.dept_id, s.noon_type, s.id
                        """)
                .param("workDate", workDate)
                .param("deptId", deptId)
                .param("noonType", noonType)
                .param("registLevelId", registLevelId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("schedulingId", rs.getLong("scheduling_id"));
                    row.put("deptId", rs.getLong("dept_id"));
                    row.put("deptName", rs.getString("dept_name"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("doctorName", rs.getString("doctor_name"));
                    row.put("registLevelId", rs.getLong("regist_level_id"));
                    row.put("levelName", rs.getString("level_name"));
                    row.put("registFee", rs.getBigDecimal("regist_fee"));
                    row.put("remainQuota", rs.getInt("remain_quota"));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> findRegistrarSchedules(Long deptId, Long employeeId, Long registLevelId,
                                                             java.time.LocalDate fromDate, java.time.LocalDate toDate,
                                                             Integer noonType) {
        return jdbcClient.sql("""
                        SELECT s.id AS scheduling_id,
                               e.dept_id,
                               d.dept_name,
                               s.employee_id,
                               e.real_name AS employee_name,
                               e.title AS employee_title,
                               s.regist_level_id,
                               rl.level_name AS regist_level_name,
                               rl.regist_fee,
                               s.work_date,
                               s.noon_type,
                               s.total_quota,
                               s.used_quota,
                               (s.total_quota - s.used_quota) AS remain_quota
                        FROM scheduling s
                        JOIN employee e ON s.employee_id = e.id
                        JOIN department d ON e.dept_id = d.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.publish_status = 1
                          AND s.work_date >= :fromDate
                          AND s.work_date <= :toDate
                          AND e.delmark = 0
                          AND (CAST(:noonType AS INTEGER) IS NULL OR s.noon_type >= CAST(:noonType AS INTEGER))
                          AND (CAST(:deptId AS BIGINT) IS NULL OR e.dept_id = CAST(:deptId AS BIGINT))
                          AND (CAST(:employeeId AS BIGINT) IS NULL OR s.employee_id = CAST(:employeeId AS BIGINT))
                          AND (CAST(:registLevelId AS BIGINT) IS NULL OR s.regist_level_id = CAST(:registLevelId AS BIGINT))
                        ORDER BY s.work_date, s.noon_type, e.dept_id, s.id
                        """)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .param("noonType", noonType)
                .param("deptId", deptId)
                .param("employeeId", employeeId)
                .param("registLevelId", registLevelId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("schedulingId", rs.getLong("scheduling_id"));
                    row.put("deptId", rs.getLong("dept_id"));
                    row.put("deptName", rs.getString("dept_name"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("employeeName", rs.getString("employee_name"));
                    row.put("employeeTitle", rs.getString("employee_title"));
                    row.put("registLevelId", rs.getLong("regist_level_id"));
                    row.put("registLevelName", rs.getString("regist_level_name"));
                    row.put("registFee", rs.getBigDecimal("regist_fee"));
                    row.put("workDate", rs.getObject("work_date", LocalDate.class));
                    row.put("noonType", rs.getInt("noon_type"));
                    row.put("totalQuota", rs.getInt("total_quota"));
                    row.put("usedQuota", rs.getInt("used_quota"));
                    row.put("remainQuota", rs.getInt("remain_quota"));
                    return row;
                })
                .list();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long schedulingId) {
        return jdbcClient.sql("""
                        SELECT s.id, e.dept_id, d.dept_name, s.employee_id, e.real_name AS doctor_name,
                               s.regist_level_id, rl.level_name, s.work_date, s.noon_type,
                               s.total_quota, s.used_quota, rl.regist_fee
                        FROM scheduling s
                        JOIN employee e ON s.employee_id = e.id
                        JOIN department d ON e.dept_id = d.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.id = :id AND s.publish_status = 1
                        FOR UPDATE
                        """)
                .param("id", schedulingId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("deptId", rs.getLong("dept_id"));
                    row.put("deptName", rs.getString("dept_name"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("doctorName", rs.getString("doctor_name"));
                    row.put("registLevelId", rs.getLong("regist_level_id"));
                    row.put("levelName", rs.getString("level_name"));
                    row.put("workDate", rs.getObject("work_date", LocalDate.class));
                    row.put("noonType", rs.getInt("noon_type"));
                    row.put("totalQuota", rs.getInt("total_quota"));
                    row.put("usedQuota", rs.getInt("used_quota"));
                    row.put("registFee", rs.getBigDecimal("regist_fee"));
                    return row;
                })
                .optional();
    }

    public void incrementUsedQuota(Long schedulingId) {
        jdbcClient.sql("""
                        UPDATE scheduling
                        SET used_quota = used_quota + 1, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", schedulingId)
                .update();
    }

    public void decrementUsedQuota(Long schedulingId) {
        jdbcClient.sql("""
                        UPDATE scheduling
                        SET used_quota = GREATEST(used_quota - 1, 0), update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", schedulingId)
                .update();
    }
}
