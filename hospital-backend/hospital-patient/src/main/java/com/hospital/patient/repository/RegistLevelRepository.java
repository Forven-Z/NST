package com.hospital.patient.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RegistLevelRepository {

    private final JdbcClient jdbcClient;

    public RegistLevelRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listAll() {
        return jdbcClient.sql("""
                        SELECT id, level_code, level_name, regist_fee
                        FROM regist_level
                        WHERE delmark = 0
                        ORDER BY id
                        """)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("levelCode", rs.getString("level_code"));
                    row.put("levelName", rs.getString("level_name"));
                    row.put("registFee", rs.getBigDecimal("regist_fee"));
                    return row;
                })
                .list();
    }
}
