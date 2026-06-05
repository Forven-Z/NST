package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DepartmentRepository {

    private final JdbcClient jdbcClient;

    public DepartmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listOutpatientDepartments() {
        return jdbcClient.sql("""
                        SELECT id, dept_code, dept_name, dept_type, sort_no
                        FROM department
                        WHERE delmark = 0 AND dept_type = 1
                        ORDER BY sort_no, id
                        """)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("deptCode", rs.getString("dept_code"));
                    row.put("deptName", rs.getString("dept_name"));
                    row.put("deptType", rs.getInt("dept_type"));
                    row.put("sortNo", rs.getObject("sort_no", Integer.class));
                    return row;
                })
                .list();
    }
}
