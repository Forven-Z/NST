package com.hospital.management.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class LeaveRequestRepository {

    private final JdbcClient jdbcClient;

    public LeaveRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insert(Long schedulingId, Long employeeId, String reason) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO scheduling_leave_request (scheduling_id, employee_id, reason, status)
                        VALUES (:schedulingId, :employeeId, :reason, 0)
                        """)
                .param("schedulingId", schedulingId)
                .param("employeeId", employeeId)
                .param("reason", reason)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to insert leave request");
        }
        return key.longValue();
    }

    public Optional<Map<String, Object>> findById(Long id) {
        return jdbcClient.sql("""
                        SELECT lr.id AS leave_request_id,
                               lr.scheduling_id,
                               lr.employee_id,
                               e.real_name AS employee_name,
                               lr.reason,
                               lr.status,
                               lr.approve_time,
                               lr.reject_remark,
                               lr.substitute_employee_id,
                               lr.substitute_time,
                               lr.create_time,
                               s.work_date,
                               s.noon_type,
                               s.total_quota,
                               s.used_quota,
                               s.employee_id AS schedule_employee_id,
                               s.regist_level_id,
                               rl.level_name AS regist_level_name,
                               emp.dept_id
                        FROM scheduling_leave_request lr
                        JOIN scheduling s ON lr.scheduling_id = s.id
                        JOIN employee e ON lr.employee_id = e.id
                        JOIN employee emp ON s.employee_id = emp.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE lr.id = :id
                        """)
                .param("id", id)
                .query(this::mapLeaveRow)
                .optional();
    }

    public Optional<Map<String, Object>> findApprovedBySchedulingId(Long schedulingId) {
        return jdbcClient.sql("""
                        SELECT lr.id AS leave_request_id,
                               lr.scheduling_id,
                               lr.employee_id,
                               e.real_name AS employee_name,
                               lr.reason,
                               lr.status,
                               lr.approve_time,
                               lr.reject_remark,
                               lr.create_time,
                               s.work_date,
                               s.noon_type,
                               s.total_quota,
                               s.used_quota,
                               rl.level_name AS regist_level_name,
                               emp.dept_id
                        FROM scheduling_leave_request lr
                        JOIN scheduling s ON lr.scheduling_id = s.id
                        JOIN employee e ON lr.employee_id = e.id
                        JOIN employee emp ON s.employee_id = emp.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE lr.scheduling_id = :schedulingId
                          AND lr.status = 1
                        ORDER BY lr.id DESC
                        LIMIT 1
                        """)
                .param("schedulingId", schedulingId)
                .query(this::mapLeaveRow)
                .optional();
    }

    public List<Map<String, Object>> listAdmin(Integer status) {
        return jdbcClient.sql("""
                        SELECT lr.id AS leave_request_id,
                               lr.scheduling_id,
                               lr.employee_id,
                               e.real_name AS employee_name,
                               lr.reason,
                               lr.status,
                               lr.approve_time,
                               lr.reject_remark,
                               lr.create_time,
                               s.work_date,
                               s.noon_type,
                               s.total_quota,
                               s.used_quota,
                               rl.level_name AS regist_level_name,
                               emp.dept_id
                        FROM scheduling_leave_request lr
                        JOIN scheduling s ON lr.scheduling_id = s.id
                        JOIN employee e ON lr.employee_id = e.id
                        JOIN employee emp ON s.employee_id = emp.id
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE (CAST(:status AS INTEGER) IS NULL OR lr.status = CAST(:status AS INTEGER))
                        ORDER BY lr.create_time DESC
                        LIMIT 100
                        """)
                .param("status", status)
                .query(this::mapLeaveRow)
                .list();
    }

    public Optional<Map<String, Object>> findActiveBySchedulingId(Long schedulingId) {
        return jdbcClient.sql("""
                        SELECT id AS leave_request_id, scheduling_id, employee_id, reason, status
                        FROM scheduling_leave_request
                        WHERE scheduling_id = :schedulingId AND status IN (0, 1)
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .param("schedulingId", schedulingId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("leaveRequestId", rs.getLong("leave_request_id"));
                    row.put("schedulingId", rs.getLong("scheduling_id"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("reason", rs.getString("reason"));
                    row.put("status", rs.getInt("status"));
                    return row;
                })
                .optional();
    }

    public Optional<Map<String, Object>> findLatestBySchedulingId(Long schedulingId) {
        return jdbcClient.sql("""
                        SELECT id AS leave_request_id, scheduling_id, employee_id, reason, status
                        FROM scheduling_leave_request
                        WHERE scheduling_id = :schedulingId
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .param("schedulingId", schedulingId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("leaveRequestId", rs.getLong("leave_request_id"));
                    row.put("schedulingId", rs.getLong("scheduling_id"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("reason", rs.getString("reason"));
                    row.put("status", rs.getInt("status"));
                    return row;
                })
                .optional();
    }

    public int updateStatus(Long id, int status, String rejectRemark) {
        return jdbcClient.sql("""
                        UPDATE scheduling_leave_request
                        SET status = :status,
                            approve_time = NOW(),
                            reject_remark = COALESCE(:rejectRemark, reject_remark)
                        WHERE id = :id AND status = 0
                        """)
                .param("id", id)
                .param("status", status)
                .param("rejectRemark", rejectRemark)
                .update();
    }

    public int cancel(Long id, Long employeeId) {
        return jdbcClient.sql("""
                        UPDATE scheduling_leave_request
                        SET status = 3, approve_time = NOW()
                        WHERE id = :id AND employee_id = :employeeId AND status = 0
                        """)
                .param("id", id)
                .param("employeeId", employeeId)
                .update();
    }

    public int markSubstituted(Long schedulingId, Long substituteEmployeeId) {
        return jdbcClient.sql("""
                        UPDATE scheduling_leave_request
                        SET status = 4,
                            substitute_employee_id = :substituteEmployeeId,
                            substitute_time = NOW()
                        WHERE scheduling_id = :schedulingId AND status = 1
                        """)
                .param("schedulingId", schedulingId)
                .param("substituteEmployeeId", substituteEmployeeId)
                .update();
    }

    public boolean hasActiveLeave(Long schedulingId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM scheduling_leave_request
                        WHERE scheduling_id = :schedulingId AND status IN (0, 1)
                        """)
                .param("schedulingId", schedulingId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private Map<String, Object> mapLeaveRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("leaveRequestId", rs.getLong("leave_request_id"));
        row.put("schedulingId", rs.getLong("scheduling_id"));
        row.put("employeeId", rs.getLong("employee_id"));
        row.put("employeeName", rs.getString("employee_name"));
        row.put("reason", rs.getString("reason"));
        row.put("status", rs.getInt("status"));
        row.put("rejectRemark", rs.getString("reject_remark"));
        row.put("workDate", rs.getObject("work_date", java.time.LocalDate.class));
        row.put("noonType", rs.getInt("noon_type"));
        int total = rs.getInt("total_quota");
        int used = rs.getInt("used_quota");
        row.put("totalQuota", total);
        row.put("usedQuota", used);
        row.put("remainQuota", total - used);
        row.put("registLevelName", rs.getString("regist_level_name"));
        row.put("deptId", rs.getLong("dept_id"));
        row.put("createTime", rs.getObject("create_time", java.time.OffsetDateTime.class));
        row.put("approveTime", rs.getObject("approve_time", java.time.OffsetDateTime.class));
        return row;
    }
}
