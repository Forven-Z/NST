package com.hospital.lis.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InspectionRequestRepository {

    private final JdbcClient jdbcClient;

    public InspectionRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> findQueue(Integer status, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT ir.id AS inspection_request_id,
                               ir.register_id,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               mt.item_name,
                               ir.item_price,
                               ir.status,
                               ir.order_time
                        FROM inspection_request ir
                        JOIN patient p ON ir.patient_id = p.id
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        WHERE ir.delmark = 0
                          AND (CAST(:status AS INTEGER) IS NULL OR ir.status = CAST(:status AS INTEGER))
                        ORDER BY ir.order_time
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("inspectionRequestId", rs.getLong("inspection_request_id"));
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
                        FROM inspection_request
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
                        UPDATE inspection_request
                        SET status = 30, executor_id = :executorId, execute_time = :now, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("executorId", executorId)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public Optional<Map<String, Object>> findResultDetail(Long id) {
        return jdbcClient.sql("""
                        SELECT ir.id AS inspection_request_id,
                               ir.status,
                               ir.result_text,
                               ir.result_attachment,
                               ir.result_time,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               mt.item_name
                        FROM inspection_request ir
                        JOIN patient p ON ir.patient_id = p.id
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        WHERE ir.id = :id AND ir.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("inspectionRequestId", rs.getLong("inspection_request_id"));
                    row.put("status", rs.getInt("status"));
                    row.put("resultText", rs.getString("result_text"));
                    row.put("resultAttachment", rs.getString("result_attachment"));
                    row.put("reportTime", rs.getObject("result_time", OffsetDateTime.class));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("itemName", rs.getString("item_name"));
                    return row;
                })
                .optional();
    }

    public void saveResult(Long id, Long resultInputId, String resultText, String resultAttachment) {
        jdbcClient.sql("""
                        UPDATE inspection_request
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
}
