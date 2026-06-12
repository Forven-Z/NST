package com.hospital.disposal.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DisposalRequestRepository {

    private final JdbcClient jdbcClient;

    public DisposalRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> findQueue(Integer status, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT dr.id AS disposal_request_id,
                               dr.register_id,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               mt.item_name,
                               dr.item_price,
                               dr.status,
                               dr.order_time
                        FROM disposal_request dr
                        JOIN patient p ON dr.patient_id = p.id
                        JOIN medical_technology mt ON dr.medical_technology_id = mt.id
                        WHERE dr.delmark = 0
                          AND (CAST(:status AS INTEGER) IS NULL OR dr.status = CAST(:status AS INTEGER))
                        ORDER BY dr.order_time
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("disposalRequestId", rs.getLong("disposal_request_id"));
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
                        SELECT id, status
                        FROM disposal_request
                        WHERE id = :id AND delmark = 0
                        FOR UPDATE
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("status", rs.getInt("status"));
                    return row;
                })
                .optional();
    }

    public void markExecuted(Long id, Long executorId) {
        jdbcClient.sql("""
                        UPDATE disposal_request
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
                        UPDATE disposal_request
                        SET status = 40,
                            result_input_id = :resultInputId,
                            result_time = :now,
                            result_text = :resultText,
                            result_attachment = :resultAttachment,
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("resultInputId", resultInputId)
                .param("now", OffsetDateTime.now())
                .param("resultText", resultText)
                .param("resultAttachment", resultAttachment)
                .update();
    }

    public Optional<Map<String, Object>> findResultDetail(Long id) {
        return jdbcClient.sql("""
                        SELECT dr.id AS disposal_request_id,
                               dr.status,
                               dr.result_text,
                               dr.result_attachment,
                               dr.result_time,
                               dr.purpose,
                               dr.body_part,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               mt.item_name
                        FROM disposal_request dr
                        JOIN patient p ON dr.patient_id = p.id
                        JOIN medical_technology mt ON dr.medical_technology_id = mt.id
                        WHERE dr.id = :id AND dr.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("disposalRequestId", rs.getLong("disposal_request_id"));
                    row.put("status", rs.getInt("status"));
                    row.put("resultText", rs.getString("result_text"));
                    row.put("resultAttachment", rs.getString("result_attachment"));
                    row.put("resultTime", rs.getObject("result_time", OffsetDateTime.class));
                    row.put("purpose", rs.getString("purpose"));
                    row.put("bodyPart", rs.getString("body_part"));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("itemName", rs.getString("item_name"));
                    return row;
                })
                .optional();
    }
}
