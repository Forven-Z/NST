package com.hospital.patient.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class ImagingStudyRepository {

    private final JdbcClient jdbcClient;

    public ImagingStudyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Map<String, Object>> findByCheckRequestId(Long checkRequestId) {
        return jdbcClient.sql("""
                        SELECT id, check_request_id, modality, status,
                               report_json::text AS report_json
                        FROM imaging_study
                        WHERE check_request_id = :checkRequestId
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .param("checkRequestId", checkRequestId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("checkRequestId", rs.getLong("check_request_id"));
                    row.put("modality", rs.getString("modality"));
                    row.put("status", rs.getString("status"));
                    row.put("reportJson", rs.getString("report_json"));
                    return row;
                })
                .optional();
    }
}
