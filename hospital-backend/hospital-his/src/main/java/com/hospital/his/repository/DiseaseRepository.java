package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DiseaseRepository {

    private final JdbcClient jdbcClient;

    public DiseaseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> listDiseases(String keyword, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT id, disease_code, disease_name, disease_category
                        FROM disease
                        WHERE delmark = 0
                          AND (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                               OR disease_name ILIKE :pattern OR disease_code ILIKE :pattern)
                        ORDER BY id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("keyword", keyword)
                .param("pattern", likePattern(keyword))
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("diseaseCode", rs.getString("disease_code"));
                    row.put("diseaseName", rs.getString("disease_name"));
                    row.put("diseaseCategory", rs.getString("disease_category"));
                    return row;
                })
                .list();
    }

    private String likePattern(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }
}
