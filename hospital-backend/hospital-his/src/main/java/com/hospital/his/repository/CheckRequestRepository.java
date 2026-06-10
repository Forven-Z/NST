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
public class CheckRequestRepository {

    private final JdbcClient jdbcClient;

    public CheckRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insert(Long registerId, Long patientId, Long medicalTechnologyId, Long doctorId,
                       BigDecimal itemPrice, String purpose, String bodyPart, String remark, int status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO check_request (register_id, patient_id, medical_technology_id, doctor_id,
                                                   item_price, purpose, body_part, remark, status)
                        VALUES (:registerId, :patientId, :medicalTechnologyId, :doctorId,
                                :itemPrice, :purpose, :bodyPart, :remark, :status)
                        """)
                .param("registerId", registerId)
                .param("patientId", patientId)
                .param("medicalTechnologyId", medicalTechnologyId)
                .param("doctorId", doctorId)
                .param("itemPrice", itemPrice)
                .param("purpose", purpose)
                .param("bodyPart", bodyPart)
                .param("remark", remark)
                .param("status", status)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public void updateStatus(Long id, int status) {
        jdbcClient.sql("""
                        UPDATE check_request SET status = :status, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .param("status", status)
                .update();
    }

    public Optional<Map<String, Object>> findById(Long id) {
        return findDetailById(id);
    }

    public Optional<Map<String, Object>> findDetailById(Long id) {
        return jdbcClient.sql("""
                        SELECT cr.id, cr.register_id, cr.patient_id, cr.status,
                               cr.purpose, cr.body_part, cr.remark,
                               cr.result_text, cr.result_time, mt.item_name
                        FROM check_request cr
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        WHERE cr.id = :id AND cr.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapDetailRow(rs))
                .optional();
    }

    public java.util.List<Map<String, Object>> findByRegisterId(Long registerId) {
        return jdbcClient.sql("""
                        SELECT cr.id, cr.register_id, cr.patient_id, cr.status,
                               cr.purpose, cr.body_part, cr.remark,
                               cr.result_text, cr.result_time, mt.item_name
                        FROM check_request cr
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        WHERE cr.register_id = :registerId AND cr.delmark = 0
                        ORDER BY cr.create_time ASC
                        """)
                .param("registerId", registerId)
                .query((rs, rowNum) -> mapDetailRow(rs))
                .list();
    }

    /** 患者端：已出结果的检查报告列表 */
    public java.util.List<Map<String, Object>> findResultsByPatient(Long patientId) {
        return jdbcClient.sql("""
                        SELECT cr.id, cr.register_id, cr.patient_id, cr.status,
                               cr.purpose, cr.body_part,
                               cr.result_text, cr.result_time, mt.item_name
                        FROM check_request cr
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        WHERE cr.patient_id = :patientId
                          AND cr.status >= :resultReady
                          AND cr.delmark = 0
                        ORDER BY cr.result_time DESC NULLS LAST, cr.update_time DESC
                        """)
                .param("patientId", patientId)
                .param("resultReady", com.hospital.common.constant.InspectionRequestStatus.RESULT_READY)
                .query((rs, rowNum) -> mapDetailRow(rs))
                .list();
    }

    private Map<String, Object> mapDetailRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("checkRequestId", rs.getLong("id"));
        row.put("registerId", rs.getLong("register_id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("status", rs.getInt("status"));
        row.put("purpose", rs.getString("purpose"));
        row.put("bodyPart", rs.getString("body_part"));
        row.put("remark", rs.getString("remark"));
        row.put("resultText", rs.getString("result_text"));
        row.put("resultTime", rs.getObject("result_time", OffsetDateTime.class));
        row.put("itemName", rs.getString("item_name"));
        return row;
    }

    public Optional<Map<String, Object>> findByIdAndDoctor(Long id, Long doctorId) {
        return jdbcClient.sql("""
                        SELECT cr.id, cr.status, cr.result_text, cr.result_time, mt.item_name
                        FROM check_request cr
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        WHERE cr.id = :id AND cr.doctor_id = :doctorId AND cr.delmark = 0
                        """)
                .param("id", id)
                .param("doctorId", doctorId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("checkRequestId", rs.getLong("id"));
                    row.put("status", rs.getInt("status"));
                    row.put("resultText", rs.getString("result_text"));
                    row.put("resultTime", rs.getObject("result_time", OffsetDateTime.class));
                    row.put("itemName", rs.getString("item_name"));
                    return row;
                })
                .optional();
    }
}
