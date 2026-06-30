package com.hospital.management.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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

    public List<Map<String, Object>> listAdminSchedules(Long deptId, Long employeeId, LocalDate workDate,
                                                        Integer publishStatus) {
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
                               s.publish_status
                        FROM scheduling s
                        JOIN employee e ON s.employee_id = e.id
                        JOIN department d ON e.dept_id = d.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE e.delmark = 0
                          AND (CAST(:deptId AS BIGINT) IS NULL OR e.dept_id = CAST(:deptId AS BIGINT))
                          AND (CAST(:employeeId AS BIGINT) IS NULL OR s.employee_id = CAST(:employeeId AS BIGINT))
                          AND (CAST(:workDate AS DATE) IS NULL OR s.work_date = CAST(:workDate AS DATE))
                          AND (CAST(:publishStatus AS INTEGER) IS NULL
                               OR s.publish_status = CAST(:publishStatus AS INTEGER))
                        ORDER BY s.work_date, s.noon_type, s.id
                        """)
                .param("deptId", deptId)
                .param("employeeId", employeeId)
                .param("workDate", workDate)
                .param("publishStatus", publishStatus)
                .query(this::mapScheduleRow)
                .list();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long id) {
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
                               s.publish_status
                        FROM scheduling s
                        JOIN employee e ON s.employee_id = e.id
                        JOIN department d ON e.dept_id = d.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.id = :id
                        """)
                .param("id", id)
                .query(this::mapScheduleRow)
                .optional();
    }

    public long insert(Long employeeId, Long registLevelId, LocalDate workDate, Integer noonType, Integer totalQuota) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO scheduling (employee_id, regist_level_id, work_date, noon_type,
                                                total_quota, used_quota, publish_status)
                        VALUES (:employeeId, :registLevelId, :workDate, :noonType, :totalQuota, 0, 0)
                        """)
                .param("employeeId", employeeId)
                .param("registLevelId", registLevelId)
                .param("workDate", workDate)
                .param("noonType", noonType)
                .param("totalQuota", totalQuota)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public int update(Long id, Long employeeId, Integer totalQuota, Integer publishStatus) {
        return jdbcClient.sql("""
                        UPDATE scheduling SET
                            employee_id = COALESCE(:employeeId, employee_id),
                            total_quota = COALESCE(:totalQuota, total_quota),
                            publish_status = COALESCE(:publishStatus, publish_status),
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("employeeId", employeeId)
                .param("totalQuota", totalQuota)
                .param("publishStatus", publishStatus)
                .update();
    }

    public int publish(Long id) {
        return jdbcClient.sql("""
                        UPDATE scheduling SET publish_status = 1, update_time = NOW()
                        WHERE id = :id AND publish_status = 0
                        """)
                .param("id", id)
                .update();
    }

    public List<Map<String, Object>> listMySchedules(Long employeeId, LocalDate workDateFrom) {
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
                               s.publish_status
                        FROM scheduling s
                        JOIN employee e ON s.employee_id = e.id
                        JOIN department d ON e.dept_id = d.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.employee_id = :employeeId
                          AND s.publish_status <> 2
                          AND (CAST(:workDateFrom AS DATE) IS NULL OR s.work_date >= CAST(:workDateFrom AS DATE))
                        ORDER BY s.work_date, s.noon_type
                        """)
                .param("employeeId", employeeId)
                .param("workDateFrom", workDateFrom)
                .query(this::mapScheduleRow)
                .list();
    }

    public List<Map<String, Object>> listWeekByDept(Long deptId, LocalDate weekStart, LocalDate weekEnd) {
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
                               s.publish_status
                        FROM scheduling s
                        JOIN employee e ON s.employee_id = e.id
                        JOIN department d ON e.dept_id = d.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE e.dept_id = :deptId
                          AND e.delmark = 0
                          AND s.work_date BETWEEN :weekStart AND :weekEnd
                          AND s.publish_status <> 2
                        ORDER BY s.work_date, s.employee_id, s.noon_type
                        """)
                .param("deptId", deptId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEnd)
                .query(this::mapScheduleRow)
                .list();
    }

    public boolean existsActiveSlot(Long employeeId, LocalDate workDate, Integer noonType) {
        return jdbcClient.sql("""
                        SELECT 1 FROM scheduling
                        WHERE employee_id = :employeeId
                          AND work_date = :workDate
                          AND noon_type = :noonType
                          AND publish_status <> 2
                        LIMIT 1
                        """)
                .param("employeeId", employeeId)
                .param("workDate", workDate)
                .param("noonType", noonType)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    public int updateDraft(Long id, Long registLevelId, Integer totalQuota) {
        return jdbcClient.sql("""
                        UPDATE scheduling SET
                            regist_level_id = COALESCE(:registLevelId, regist_level_id),
                            total_quota = COALESCE(:totalQuota, total_quota),
                            update_time = NOW()
                        WHERE id = :id AND publish_status = 0
                        """)
                .param("id", id)
                .param("registLevelId", registLevelId)
                .param("totalQuota", totalQuota)
                .update();
    }

    public int updatePublishedQuota(Long id, Integer totalQuota) {
        return jdbcClient.sql("""
                        UPDATE scheduling SET total_quota = :totalQuota, update_time = NOW()
                        WHERE id = :id AND publish_status = 1 AND used_quota <= :totalQuota
                        """)
                .param("id", id)
                .param("totalQuota", totalQuota)
                .update();
    }

    public int cancelDraft(Long id) {
        return jdbcClient.sql("""
                        UPDATE scheduling SET publish_status = 2, update_time = NOW()
                        WHERE id = :id AND publish_status = 0 AND used_quota = 0
                        """)
                .param("id", id)
                .update();
    }

    public int batchPublishWeek(Long deptId, LocalDate weekStart, LocalDate weekEnd) {
        return jdbcClient.sql("""
                        UPDATE scheduling s SET publish_status = 1, update_time = NOW()
                        FROM employee e
                        WHERE s.employee_id = e.id
                          AND e.dept_id = :deptId
                          AND s.work_date BETWEEN :weekStart AND :weekEnd
                          AND s.publish_status = 0
                        """)
                .param("deptId", deptId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEnd)
                .update();
    }

    private Map<String, Object> mapScheduleRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
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
        row.put("publishStatus", rs.getInt("publish_status"));
        return row;
    }
}
