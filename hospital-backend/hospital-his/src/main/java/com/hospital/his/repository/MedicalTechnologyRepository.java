package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MedicalTechnologyRepository {

    private final JdbcClient jdbcClient;

    public MedicalTechnologyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listMedicalTechnologies(String keyword, String techType, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, item_code, item_name, tech_type, price, dept_id
                        FROM medical_technology
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR item_name ILIKE :pattern OR item_code ILIKE :pattern)
                          AND (CAST(:techType AS VARCHAR) IS NULL OR tech_type = CAST(:techType AS VARCHAR))
                        ORDER BY tech_type, id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("techType", techType)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("itemCode", rs.getString("item_code"));
                    row.put("itemName", rs.getString("item_name"));
                    row.put("techType", rs.getString("tech_type"));
                    row.put("price", rs.getBigDecimal("price"));
                    row.put("deptId", rs.getObject("dept_id", Long.class));
                    return row;
                })
                .list();
    }

    public Optional<Map<String, Object>> findInspectionItem(Long id) {
        return findByTechType(id, "INSPECTION");
    }

    public Optional<Map<String, Object>> findCheckItem(Long id) {
        return findByTechType(id, "CHECK");
    }

    public Optional<Map<String, Object>> findDisposalItem(Long id) {
        return findByTechType(id, "DISPOSAL");
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

    private String likePattern(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
