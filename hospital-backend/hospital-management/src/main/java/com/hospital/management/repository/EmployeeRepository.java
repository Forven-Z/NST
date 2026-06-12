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
public class EmployeeRepository {

    private final JdbcClient jdbcClient;

    public EmployeeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listEmployees(String keyword, Long deptId, String roleType,
                                                    Integer delmark, Integer scheduleKind,
                                                    int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT e.id AS employee_id, e.emp_no, e.real_name, e.gender, e.dept_id, d.dept_name,
                               e.title, e.role_type, e.phone, e.delmark,
                               u.username, u.status AS account_status
                        FROM employee e
                        JOIN department d ON e.dept_id = d.id
                        LEFT JOIN sys_user u ON u.employee_id = e.id AND u.delmark = 0
                        WHERE (CAST(:delmark AS INTEGER) IS NULL OR e.delmark = CAST(:delmark AS INTEGER))
                          AND (CAST(:deptId AS BIGINT) IS NULL OR e.dept_id = CAST(:deptId AS BIGINT))
                          AND (CAST(:roleType AS VARCHAR) IS NULL OR CAST(:roleType AS VARCHAR) = ''
                               OR e.role_type = CAST(:roleType AS VARCHAR))
                          AND (CAST(:scheduleKind AS INTEGER) IS NULL
                               OR (CAST(:scheduleKind AS INTEGER) = 1 AND e.role_type = 'OUTPATIENT_DOCTOR'))
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR e.real_name ILIKE :pattern OR e.emp_no ILIKE :pattern
                               OR u.username ILIKE :pattern)
                        ORDER BY e.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("deptId", deptId)
                .param("roleType", roleType)
                .param("delmark", delmark)
                .param("scheduleKind", scheduleKind)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapEmployeeRow)
                .list();
    }

    public Optional<Map<String, Object>> findById(Long id) {
        return jdbcClient.sql("""
                        SELECT e.id AS employee_id, e.emp_no, e.real_name, e.gender, e.dept_id, d.dept_name,
                               e.title, e.role_type, e.phone, e.delmark,
                               u.username, u.status AS account_status
                        FROM employee e
                        JOIN department d ON e.dept_id = d.id
                        LEFT JOIN sys_user u ON u.employee_id = e.id AND u.delmark = 0
                        WHERE e.id = :id
                        """)
                .param("id", id)
                .query(this::mapEmployeeRow)
                .optional();
    }

    public long insert(String empNo, String realName, Integer gender, Long deptId,
                       String title, String roleType, String phone) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO employee (emp_no, real_name, gender, dept_id, title, role_type, phone)
                        VALUES (:empNo, :realName, :gender, :deptId, :title, :roleType, :phone)
                        """)
                .param("empNo", empNo)
                .param("realName", realName)
                .param("gender", gender)
                .param("deptId", deptId)
                .param("title", title)
                .param("roleType", roleType)
                .param("phone", phone)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public int update(Long id, String empNo, String realName, Integer gender, Long deptId,
                      String title, String roleType, String phone) {
        return jdbcClient.sql("""
                        UPDATE employee SET
                            emp_no = COALESCE(:empNo, emp_no),
                            real_name = COALESCE(:realName, real_name),
                            gender = COALESCE(:gender, gender),
                            dept_id = COALESCE(:deptId, dept_id),
                            title = COALESCE(:title, title),
                            role_type = COALESCE(:roleType, role_type),
                            phone = COALESCE(:phone, phone),
                            update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .param("empNo", empNo)
                .param("realName", realName)
                .param("gender", gender)
                .param("deptId", deptId)
                .param("title", title)
                .param("roleType", roleType)
                .param("phone", phone)
                .update();
    }

    public int softDelete(Long id) {
        return jdbcClient.sql("""
                        UPDATE employee SET delmark = 1, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .update();
    }

    public void deleteById(Long id) {
        jdbcClient.sql("DELETE FROM employee WHERE id = :id")
                .param("id", id)
                .update();
    }

    public boolean existsActive(Long id) {
        return Boolean.TRUE.equals(jdbcClient.sql(
                        "SELECT EXISTS(SELECT 1 FROM employee WHERE id = :id AND delmark = 0)")
                .param("id", id)
                .query(Boolean.class)
                .single());
    }

    public Optional<Long> findDeptId(Long employeeId) {
        return jdbcClient.sql("SELECT dept_id FROM employee WHERE id = :id AND delmark = 0")
                .param("id", employeeId)
                .query(Long.class)
                .optional();
    }

    public Optional<String> findRoleType(Long employeeId) {
        return jdbcClient.sql("SELECT role_type FROM employee WHERE id = :id AND delmark = 0")
                .param("id", employeeId)
                .query(String.class)
                .optional();
    }

    private Map<String, Object> mapEmployeeRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("employeeId", rs.getLong("employee_id"));
        row.put("empNo", rs.getString("emp_no"));
        row.put("realName", rs.getString("real_name"));
        row.put("gender", rs.getObject("gender", Integer.class));
        row.put("deptId", rs.getLong("dept_id"));
        row.put("deptName", rs.getString("dept_name"));
        row.put("title", rs.getString("title"));
        row.put("roleType", rs.getString("role_type"));
        row.put("phone", rs.getString("phone"));
        row.put("delmark", rs.getInt("delmark"));
        row.put("username", rs.getString("username"));
        row.put("accountStatus", rs.getObject("account_status", Integer.class));
        return row;
    }

    private String likePattern(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
