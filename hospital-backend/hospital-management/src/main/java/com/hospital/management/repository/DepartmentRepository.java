package com.hospital.management.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class DepartmentRepository {

    private final JdbcClient jdbcClient;

    public DepartmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insert(String deptCode, String deptName, Integer deptType, Integer sortNo) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO department (dept_code, dept_name, dept_type, sort_no)
                        VALUES (:deptCode, :deptName, :deptType, :sortNo)
                        """)
                .param("deptCode", deptCode)
                .param("deptName", deptName)
                .param("deptType", deptType)
                .param("sortNo", sortNo)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public int update(Long id, String deptName, Integer deptType, Integer sortNo) {
        return jdbcClient.sql("""
                        UPDATE department SET dept_name = :deptName, dept_type = :deptType,
                            sort_no = :sortNo, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .param("deptName", deptName)
                .param("deptType", deptType)
                .param("sortNo", sortNo)
                .update();
    }

    public int softDelete(Long id) {
        return jdbcClient.sql("""
                        UPDATE department SET delmark = 1, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .update();
    }

    public int countEmployees(Long deptId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)::int FROM employee WHERE dept_id = :deptId AND delmark = 0
                        """)
                .param("deptId", deptId)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }

    public Optional<Map<String, Object>> findById(Long id) {
        return jdbcClient.sql("""
                        SELECT id, dept_code, dept_name, dept_type, sort_no
                        FROM department WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("deptCode", rs.getString("dept_code"));
                    row.put("deptName", rs.getString("dept_name"));
                    row.put("deptType", rs.getObject("dept_type", Integer.class));
                    row.put("sortNo", rs.getObject("sort_no", Integer.class));
                    return row;
                })
                .optional();
    }

    public boolean existsActive(Long id) {
        return Boolean.TRUE.equals(jdbcClient.sql(
                        "SELECT EXISTS(SELECT 1 FROM department WHERE id = :id AND delmark = 0)")
                .param("id", id)
                .query(Boolean.class)
                .single());
    }
}
