package com.hospital.lis.repository;

import com.hospital.common.execute.MedTechOrderStatusWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InspectionRequestRepository implements MedTechOrderStatusWriter {

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
                        SELECT id, status, medical_technology_id
                        FROM inspection_request
                        WHERE id = :id AND delmark = 0
                        FOR UPDATE
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("status", rs.getInt("status"));
                    row.put("medicalTechnologyId", rs.getLong("medical_technology_id"));
                    return row;
                })
                .optional();
    }

    public Optional<Map<String, Object>> findLabReportContext(Long id) {
        return jdbcClient.sql("""
                        SELECT ir.id AS inspection_request_id,
                               ir.register_id,
                               ir.patient_id,
                               ir.status,
                               ir.purpose,
                               ir.body_part,
                               ir.remark AS order_remark,
                               ir.result_text,
                               ir.result_time,
                               ir.execute_time,
                               ir.executor_id,
                               ir.result_input_id,
                               ir.reviewer_id,
                               mt.item_name,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               p.gender,
                               p.age,
                               d.dept_name AS department_name,
                               mr.diagnosis AS clinical_diagnosis,
                               e1.real_name AS tester_name,
                               e2.real_name AS reviewer_name,
                               rep.real_name AS reporter_name,
                               doc.real_name AS ordering_doctor_name
                        FROM inspection_request ir
                        JOIN patient p ON ir.patient_id = p.id
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        JOIN register reg ON ir.register_id = reg.id
                        JOIN department d ON reg.dept_id = d.id
                        LEFT JOIN medical_record mr ON mr.register_id = ir.register_id AND mr.delmark = 0
                        LEFT JOIN employee e1 ON ir.executor_id = e1.id
                        LEFT JOIN employee e2 ON ir.reviewer_id = e2.id
                        LEFT JOIN employee rep ON ir.result_input_id = rep.id
                        LEFT JOIN employee doc ON ir.doctor_id = doc.id
                        WHERE ir.id = :id AND ir.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapLabContext(rs))
                .optional();
    }

    public void markExecuted(Long id, Long executorId) {
        markExecutedIfCurrent(id, com.hospital.common.constant.InspectionRequestStatus.PAID, executorId);
    }

    public int markExecutedIfCurrent(Long id, int expectedFrom, Long executorId) {
        return jdbcClient.sql("""
                        UPDATE inspection_request
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
                        UPDATE inspection_request SET status = :newStatus, update_time = NOW()
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

    /** 仅更新结果字段；status 须已由 {@link com.hospital.lis.order.LisMedTechOrderCoordinator} 迁移。 */
    public void saveResultContent(Long id, Long resultInputId, Long reviewerId, String resultText,
                                  boolean reviewOnly) {
        if (reviewOnly) {
            jdbcClient.sql("""
                            UPDATE inspection_request
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
                        UPDATE inspection_request
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

    private Map<String, Object> mapLabContext(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("inspectionRequestId", rs.getLong("inspection_request_id"));
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
        row.put("testerName", rs.getString("tester_name"));
        row.put("reporterName", rs.getString("reporter_name"));
        row.put("reviewerName", rs.getString("reviewer_name"));
        row.put("resultInputId", rs.getObject("result_input_id") != null ? rs.getLong("result_input_id") : null);
        row.put("reviewerId", rs.getObject("reviewer_id") != null ? rs.getLong("reviewer_id") : null);
        row.put("orderingDoctorName", rs.getString("ordering_doctor_name"));
        return row;
    }
}
