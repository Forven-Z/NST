package com.hospital.his.repository;

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
                               s.dept_id,
                               d.dept_name,
                               s.employee_id,
                               e.real_name AS doctor_name,
                               s.regist_level_id,
                               rl.level_name,
                               rl.regist_fee,
                               (s.total_quota - s.used_quota) AS remain_quota
                        FROM scheduling s
                        JOIN department d ON s.dept_id = d.id
                        JOIN employee e ON s.employee_id = e.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.delmark = 0
                          AND s.publish_status = 1
                          AND s.work_date = :workDate
                          AND (s.total_quota - s.used_quota) > 0
                          AND (CAST(:deptId AS BIGINT) IS NULL OR s.dept_id = CAST(:deptId AS BIGINT))
                          AND (CAST(:noonType AS INTEGER) IS NULL OR s.noon_type = CAST(:noonType AS INTEGER))
                          AND (CAST(:registLevelId AS BIGINT) IS NULL OR s.regist_level_id = CAST(:registLevelId AS BIGINT))
                        ORDER BY s.dept_id, s.noon_type, s.id
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

    public Optional<Map<String, Object>> findByIdForUpdate(Long schedulingId) {
        return jdbcClient.sql("""
                        SELECT s.id, s.dept_id, s.employee_id, s.regist_level_id, s.work_date, s.noon_type,
                               s.total_quota, s.used_quota, rl.regist_fee
                        FROM scheduling s
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.id = :id AND s.delmark = 0 AND s.publish_status = 1
                        FOR UPDATE
                        """)
                .param("id", schedulingId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("deptId", rs.getLong("dept_id"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("registLevelId", rs.getLong("regist_level_id"));
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
