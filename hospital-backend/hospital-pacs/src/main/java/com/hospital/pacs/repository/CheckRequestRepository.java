package com.hospital.pacs.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CheckRequestRepository {

    private final JdbcClient jdbcClient;

    public CheckRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> findQueue(Integer status, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT cr.id AS check_request_id,
                               cr.register_id,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               mt.item_name,
                               cr.item_price,
                               cr.status,
                               cr.order_time
                        FROM check_request cr
                        JOIN patient p ON cr.patient_id = p.id
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        WHERE cr.delmark = 0
                          AND (CAST(:status AS INTEGER) IS NULL OR cr.status = CAST(:status AS INTEGER))
                        ORDER BY cr.order_time
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("checkRequestId", rs.getLong("check_request_id"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("itemName", rs.getString("item_name"));
                    row.put("itemPrice", rs.getBigDecimal("item_price"));
                    row.put("status", rs.getInt("status"));
                    row.put("orderTime", rs.getObject("order_time", OffsetDateTime.class));
                    return row;
                })
                .list();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long id) {
        return jdbcClient.sql("""
                        SELECT id, status FROM check_request
                        WHERE id = :id AND delmark = 0 FOR UPDATE
                        """)
                .param("id", id)
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "status", rs.getInt("status")))
                .optional();
    }

    public void markExecuted(Long id, Long executorId) {
        jdbcClient.sql("""
                        UPDATE check_request
                        SET status = 30, executor_id = :executorId, execute_time = :now, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("executorId", executorId)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public void saveResult(Long id, Long resultInputId, String resultText, String resultAttachment) {
        jdbcClient.sql("""
                        UPDATE check_request
                        SET status = 40, result_input_id = :resultInputId, result_time = :now,
                            result_text = :resultText, result_attachment = :resultAttachment, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("resultInputId", resultInputId)
                .param("now", OffsetDateTime.now())
                .param("resultText", resultText)
                .param("resultAttachment", resultAttachment)
                .update();
    }
}
