package com.hospital.patient.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SettleCategoryRepository {

    private final JdbcClient jdbcClient;

    public SettleCategoryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listAll() {
        return jdbcClient.sql("""
                        SELECT id, category_code, category_name
                        FROM settle_category
                        WHERE delmark = 0
                        ORDER BY id
                        """)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("categoryCode", rs.getString("category_code"));
                    row.put("categoryName", rs.getString("category_name"));
                    return row;
                })
                .list();
    }
}
