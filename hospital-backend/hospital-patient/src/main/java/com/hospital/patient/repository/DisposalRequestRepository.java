package com.hospital.patient.repository;

import com.hospital.common.constant.InspectionRequestStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 患者域只读：处置申请查询（开立/状态变更在 hospital-his）。 */
@Repository
public class DisposalRequestRepository {

    private final JdbcClient jdbcClient;

    public DisposalRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Map<String, Object>> findById(Long id) {
        return jdbcClient.sql("""
                        SELECT dr.id, dr.register_id, dr.patient_id, dr.status,
                               dr.purpose, dr.body_part, dr.result_text, dr.result_time, mt.item_name
                        FROM disposal_request dr
                        JOIN medical_technology mt ON dr.medical_technology_id = mt.id
                        WHERE dr.id = :id AND dr.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapDetailRow(rs))
                .optional();
    }

    public List<Map<String, Object>> findByRegisterId(Long registerId) {
        return jdbcClient.sql("""
                        SELECT dr.id, dr.register_id, dr.patient_id, dr.status,
                               dr.purpose, dr.body_part, dr.result_text, dr.result_time, mt.item_name
                        FROM disposal_request dr
                        JOIN medical_technology mt ON dr.medical_technology_id = mt.id
                        WHERE dr.register_id = :registerId AND dr.delmark = 0
                        ORDER BY dr.create_time ASC
                        """)
                .param("registerId", registerId)
                .query((rs, rowNum) -> mapDetailRow(rs))
                .list();
    }

    public List<Map<String, Object>> findResultsByPatient(Long patientId) {
        return jdbcClient.sql("""
                        SELECT dr.id, dr.register_id, dr.patient_id, dr.status,
                               dr.purpose, dr.body_part, dr.result_text, dr.result_time, mt.item_name
                        FROM disposal_request dr
                        JOIN medical_technology mt ON dr.medical_technology_id = mt.id
                        WHERE dr.patient_id = :patientId
                          AND dr.status >= :resultReady
                          AND dr.delmark = 0
                        ORDER BY dr.result_time DESC NULLS LAST, dr.update_time DESC
                        """)
                .param("patientId", patientId)
                .param("resultReady", InspectionRequestStatus.RESULT_READY)
                .query((rs, rowNum) -> mapDetailRow(rs))
                .list();
    }

    public int countPendingResultsByPatient(Long patientId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM disposal_request
                        WHERE patient_id = :patientId AND status >= 20 AND status < 40 AND delmark = 0
                        """)
                .param("patientId", patientId)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
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
                               mt.item_name,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               p.gender,
                               p.age,
                               d.dept_name AS department_name,
                               mr.diagnosis AS clinical_diagnosis,
                               e1.real_name AS executor_name,
                               e2.real_name AS recorder_name,
                               rev.real_name AS reviewer_name,
                               doc.real_name AS ordering_doctor_name
                        FROM disposal_request dr
                        JOIN patient p ON dr.patient_id = p.id
                        JOIN medical_technology mt ON dr.medical_technology_id = mt.id
                        JOIN register reg ON dr.register_id = reg.id
                        JOIN department d ON reg.dept_id = d.id
                        LEFT JOIN medical_record mr ON mr.register_id = dr.register_id AND mr.delmark = 0
                        LEFT JOIN employee e1 ON dr.executor_id = e1.id
                        LEFT JOIN employee e2 ON dr.result_input_id = e2.id
                        LEFT JOIN employee rev ON dr.reviewer_id = rev.id
                        LEFT JOIN employee doc ON dr.doctor_id = doc.id
                        WHERE dr.id = :id AND dr.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapRecordContext(rs))
                .optional();
    }

    private Map<String, Object> mapDetailRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("disposalRequestId", rs.getLong("id"));
        row.put("registerId", rs.getLong("register_id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("status", rs.getInt("status"));
        row.put("purpose", rs.getString("purpose"));
        row.put("bodyPart", rs.getString("body_part"));
        row.put("resultText", rs.getString("result_text"));
        row.put("resultTime", rs.getObject("result_time", OffsetDateTime.class));
        row.put("itemName", rs.getString("item_name"));
        return row;
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
        row.put("orderingDoctorName", rs.getString("ordering_doctor_name"));
        return row;
    }
}
