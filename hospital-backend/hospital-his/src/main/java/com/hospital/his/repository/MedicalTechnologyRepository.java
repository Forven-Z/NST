package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class MedicalTechnologyRepository {

    private final JdbcClient jdbcClient;

    public MedicalTechnologyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Map<String, Object>> findInspectionItem(Long id) {
        return findByTechType(id, "INSPECTION");
    }

    public Optional<Map<String, Object>> findCheckItem(Long id) {
        return findByTechType(id, "CHECK");
    }

    private Optional<Map<String, Object>> findByTechType(Long id, String techType) {
        return jdbcClient.sql("""
                        SELECT id, item_code, item_name, tech_type, price
                        FROM medical_technology
                        WHERE id = :id AND tech_type = :techType AND delmark = 0
                        """)
                .param("id", id)
                .param("techType", techType)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("itemCode", rs.getString("item_code"));
                    row.put("itemName", rs.getString("item_name"));
                    row.put("techType", rs.getString("tech_type"));
                    row.put("price", rs.getBigDecimal("price"));
                    return row;
                })
                .optional();
    }
}
