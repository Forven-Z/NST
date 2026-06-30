package com.hospital.disposal.repository;

import com.hospital.common.execute.MedTechOrderStatusWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DisposalRequestRepository implements MedTechOrderStatusWriter {

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

    public Optional<Map<String, Object>> findDisposalRecordContext(Long id) {
        return jdbcClient.sql("""
                        SELECT dr.id AS disposal_request_id,
                               dr.register_id,
                               dr.patient_id,
                               dr.status,
                               dr.purpose,
                               dr.body_part,
                               dr.remark AS order_remark,
                               dr.result_text,
                               dr.result_time,
                               dr.execute_time,
                               dr.result_input_id,
                               dr.reviewer_id,
                               mt.item_name,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               p.gender,
                               p.age,
                               d.dept_name AS department_name,
                               mr.diagnosis AS clinical_diagnosis,
                               e1.real_name AS executor_name,
                               rep.real_name AS recorder_name,
                               rev.real_name AS reviewer_name,
                               doc.real_name AS ordering_doctor_name
                        FROM disposal_request dr
                        JOIN patient p ON dr.patient_id = p.id
                        JOIN medical_technology mt ON dr.medical_technology_id = mt.id
                        JOIN register reg ON dr.register_id = reg.id
                        JOIN department d ON reg.dept_id = d.id
                        LEFT JOIN medical_record mr ON mr.register_id = dr.register_id AND mr.delmark = 0
                        LEFT JOIN employee e1 ON dr.executor_id = e1.id
                        LEFT JOIN employee rep ON dr.result_input_id = rep.id
                        LEFT JOIN employee rev ON dr.reviewer_id = rev.id
                        LEFT JOIN employee doc ON dr.doctor_id = doc.id
                        WHERE dr.id = :id AND dr.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapRecordContext(rs))
                .optional();
    }

    public void markExecuted(Long id, Long executorId) {
        markExecutedIfCurrent(id, com.hospital.common.constant.InspectionRequestStatus.PAID, executorId);
    }

    public int markExecutedIfCurrent(Long id, int expectedFrom, Long executorId) {
        return jdbcClient.sql("""
                        UPDATE disposal_request
                        SET status = 30, executor_id = :executorId, execute_time = :now, update_time = NOW()
                        WHERE id = :id AND status = :expectedFrom AND delmark = 0
                        """)
                .param("id", id)
                .param("expectedFrom", expectedFrom)
                .param("executorId", executorId)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public int updateStatusIfCurrent(Long id, int expectedFrom, int newStatus) {
        return jdbcClient.sql("""
                        UPDATE disposal_request SET status = :newStatus, update_time = NOW()
                        WHERE id = :id AND status = :expectedFrom AND delmark = 0
                        """)
                .param("id", id)
                .param("expectedFrom", expectedFrom)
                .param("newStatus", newStatus)
                .update();
    }

    public void saveResult(Long id, Long resultInputId, Long reviewerId, String resultText, boolean reviewOnly) {
        saveResultContent(id, resultInputId, reviewerId, resultText, reviewOnly);
    }

    public void saveResultContent(Long id, Long resultInputId, Long reviewerId, String resultText,
                                  boolean reviewOnly) {
        if (reviewOnly) {
            jdbcClient.sql("""
                            UPDATE disposal_request
                            SET reviewer_id = :reviewerId,
                                result_time = :now,
                                update_time = NOW()
                            WHERE id = :id
                            """)
                    .param("id", id)
                    .param("reviewerId", reviewerId)
                    .param("now", OffsetDateTime.now())
                    .update();
            return;
        }
        jdbcClient.sql("""
                        UPDATE disposal_request
                        SET result_input_id = :resultInputId,
                            reviewer_id = :reviewerId,
                            result_time = :now,
                            result_text = :resultText,
                            result_attachment = NULL,
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("resultInputId", resultInputId)
                .param("reviewerId", reviewerId)
                .param("now", OffsetDateTime.now())
                .param("resultText", resultText)
                .update();
    }

    private Map<String, Object> mapRecordContext(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("disposalRequestId", rs.getLong("disposal_request_id"));
        row.put("registerId", rs.getLong("register_id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("status", rs.getInt("status"));
        row.put("purpose", rs.getString("purpose"));
        row.put("bodyPart", rs.getString("body_part"));
        row.put("orderRemark", rs.getString("order_remark"));
        row.put("resultText", rs.getString("result_text"));
        row.put("resultTime", rs.getObject("result_time", OffsetDateTime.class));
        row.put("executeTime", rs.getObject("execute_time", OffsetDateTime.class));
        row.put("itemName", rs.getString("item_name"));
        row.put("medicalRecordNo", rs.getString("medical_record_no"));
        row.put("patientName", rs.getString("patient_name"));
        row.put("gender", rs.getObject("gender") != null ? rs.getInt("gender") : null);
        row.put("age", rs.getObject("age") != null ? rs.getInt("age") : null);
        row.put("departmentName", rs.getString("department_name"));
        row.put("clinicalDiagnosis", rs.getString("clinical_diagnosis"));
        row.put("executorName", rs.getString("executor_name"));
        row.put("recorderName", rs.getString("recorder_name"));
        row.put("reviewerName", rs.getString("reviewer_name"));
        row.put("resultInputId", rs.getObject("result_input_id") != null ? rs.getLong("result_input_id") : null);
        row.put("reviewerId", rs.getObject("reviewer_id") != null ? rs.getLong("reviewer_id") : null);
        row.put("orderingDoctorName", rs.getString("ordering_doctor_name"));
        return row;
    }
}
