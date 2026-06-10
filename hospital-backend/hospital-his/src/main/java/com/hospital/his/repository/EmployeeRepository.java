package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class EmployeeRepository {

    private final JdbcClient jdbcClient;

    public EmployeeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listOutpatientDoctorsByDept(Long deptId) {
        return jdbcClient.sql("""
                        SELECT e.id AS employee_id,
                               e.emp_no,
                               e.real_name,
                               e.title,
                               e.dept_id,
                               e.gender
                        FROM employee e
                        WHERE e.delmark = 0
                          AND e.dept_id = :deptId
                          AND e.role_type = 'OUTPATIENT_DOCTOR'
                        ORDER BY e.id
                        """)
                .param("deptId", deptId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("empNo", rs.getString("emp_no"));
                    row.put("realName", rs.getString("real_name"));
                    row.put("title", rs.getString("title"));
                    row.put("deptId", rs.getLong("dept_id"));
                    row.put("gender", rs.getObject("gender", Integer.class));
                    return row;
                })
                .list();
    }

    public int countExpertSessions(Long employeeId, int days) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)::int
                        FROM scheduling s
                        JOIN regist_level rl ON s.regist_level_id = rl.id
                        WHERE s.employee_id = :employeeId
                          AND s.publish_status = 1
                          AND rl.level_code = 'EXPERT'
                          AND s.work_date >= CURRENT_DATE
                          AND s.work_date < CURRENT_DATE + CAST(:days AS INTEGER)
                        """)
                .param("employeeId", employeeId)
                .param("days", days)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }
}
