package com.hospital.management.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SchedulingTemplateRepository {

    private final JdbcClient jdbcClient;

    public SchedulingTemplateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listByEmployee(Long employeeId) {
        return jdbcClient.sql("""
                        SELECT weekday, noon_type, regist_level_id, total_quota, enabled
                        FROM scheduling_template
                        WHERE employee_id = :employeeId
                        ORDER BY weekday, noon_type
                        """)
                .param("employeeId", employeeId)
                .query(this::mapTemplateRow)
                .list();
    }

    public List<Map<String, Object>> listEnabledForEmployees(Collection<Long> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT employee_id, weekday, noon_type, regist_level_id, total_quota
                        FROM scheduling_template
                        WHERE enabled = 1 AND employee_id = ANY(:ids)
                        """)
                .param("ids", employeeIds.toArray(new Long[0]))
                .query(this::mapEnabledTemplateRow)
                .list();
    }

    public void replaceForEmployee(Long employeeId, List<Map<String, Object>> slots) {
        jdbcClient.sql("DELETE FROM scheduling_template WHERE employee_id = :employeeId")
                .param("employeeId", employeeId)
                .update();
        for (Map<String, Object> slot : slots) {
            jdbcClient.sql("""
                            INSERT INTO scheduling_template
                                (employee_id, weekday, noon_type, regist_level_id, total_quota, enabled)
                            VALUES (:employeeId, :weekday, :noonType, :registLevelId, :totalQuota, :enabled)
                            """)
                    .param("employeeId", employeeId)
                    .param("weekday", slot.get("weekday"))
                    .param("noonType", slot.get("noonType"))
                    .param("registLevelId", slot.get("registLevelId"))
                    .param("totalQuota", slot.get("totalQuota"))
                    .param("enabled", Boolean.TRUE.equals(slot.get("enabled")) ? 1 : 0)
                    .update();
        }
    }

    public boolean hasEnabledForDept(Long deptId) {
        return jdbcClient.sql("""
                        SELECT 1 FROM scheduling_template t
                        JOIN employee e ON t.employee_id = e.id
                        WHERE e.dept_id = :deptId AND t.enabled = 1
                        LIMIT 1
                        """)
                .param("deptId", deptId)
                .query(Integer.class)
                .optional()
                .isPresent();
    }

    private Map<String, Object> mapTemplateRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("weekday", rs.getInt("weekday"));
        row.put("noonType", rs.getInt("noon_type"));
        row.put("registLevelId", rs.getLong("regist_level_id"));
        row.put("totalQuota", rs.getInt("total_quota"));
        row.put("enabled", rs.getInt("enabled"));
        return row;
    }

    private Map<String, Object> mapEnabledTemplateRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("employeeId", rs.getLong("employee_id"));
        row.put("weekday", rs.getInt("weekday"));
        row.put("noonType", rs.getInt("noon_type"));
        row.put("registLevelId", rs.getLong("regist_level_id"));
        row.put("totalQuota", rs.getInt("total_quota"));
        return row;
    }
}
