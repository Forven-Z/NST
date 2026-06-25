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
public class InspectionRequestRepository {

    private final JdbcClient jdbcClient;

    public InspectionRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insert(Long registerId, Long patientId, Long medicalTechnologyId, Long doctorId,
                       BigDecimal itemPrice, String purpose, String bodyPart, String remark, int status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO inspection_request (register_id, patient_id, medical_technology_id, doctor_id,
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
                        UPDATE inspection_request SET status = :status, update_time = NOW()
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", id)
                .param("status", status)
                .update();
    }

    public Optional<Map<String, Object>> findById(Long id) {
        return jdbcClient.sql("""
                        SELECT ir.id, ir.register_id, ir.patient_id, ir.medical_technology_id, ir.doctor_id,
                               ir.item_price, ir.purpose, ir.body_part, ir.remark, ir.status,
                               ir.result_text, ir.result_time, mt.item_name
                        FROM inspection_request ir
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        WHERE ir.id = :id AND ir.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    public Optional<Map<String, Object>> findByIdAndDoctor(Long id, Long doctorId) {
        return jdbcClient.sql("""
                        SELECT ir.id, ir.register_id, ir.patient_id, ir.medical_technology_id, ir.doctor_id,
                               ir.item_price, ir.purpose, ir.body_part, ir.remark, ir.status,
                               ir.result_text, ir.result_time, mt.item_name
                        FROM inspection_request ir
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        WHERE ir.id = :id AND ir.doctor_id = :doctorId AND ir.delmark = 0
                        """)
                .param("id", id)
                .param("doctorId", doctorId)
                .query((rs, rowNum) -> mapRow(rs))
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
                               mt.item_name,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               p.gender,
                               p.age,
                               d.dept_name AS department_name,
                               mr.diagnosis AS clinical_diagnosis,
                               e1.real_name AS tester_name,
                               rep.real_name AS reporter_name,
                               rev.real_name AS reviewer_name,
                               doc.real_name AS ordering_doctor_name
                        FROM inspection_request ir
                        JOIN patient p ON ir.patient_id = p.id
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        JOIN register reg ON ir.register_id = reg.id
                        JOIN department d ON reg.dept_id = d.id
                        LEFT JOIN medical_record mr ON mr.register_id = ir.register_id AND mr.delmark = 0
                        LEFT JOIN employee e1 ON ir.executor_id = e1.id
                        LEFT JOIN employee rep ON ir.result_input_id = rep.id
                        LEFT JOIN employee rev ON ir.reviewer_id = rev.id
                        LEFT JOIN employee doc ON ir.doctor_id = doc.id
                        WHERE ir.id = :id AND ir.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapLabContext(rs))
                .optional();
    }

    public Optional<Map<String, Object>> findLabReportContextByDoctor(Long id, Long doctorId) {
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
                               mt.item_name,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               p.gender,
                               p.age,
                               d.dept_name AS department_name,
                               mr.diagnosis AS clinical_diagnosis,
                               e1.real_name AS tester_name,
                               rep.real_name AS reporter_name,
                               rev.real_name AS reviewer_name,
                               doc.real_name AS ordering_doctor_name
                        FROM inspection_request ir
                        JOIN patient p ON ir.patient_id = p.id
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        JOIN register reg ON ir.register_id = reg.id
                        JOIN department d ON reg.dept_id = d.id
                        LEFT JOIN medical_record mr ON mr.register_id = ir.register_id AND mr.delmark = 0
                        LEFT JOIN employee e1 ON ir.executor_id = e1.id
                        LEFT JOIN employee rep ON ir.result_input_id = rep.id
                        LEFT JOIN employee rev ON ir.reviewer_id = rev.id
                        LEFT JOIN employee doc ON ir.doctor_id = doc.id
                        WHERE ir.id = :id AND ir.doctor_id = :doctorId AND ir.delmark = 0
                        """)
                .param("id", id)
                .param("doctorId", doctorId)
                .query((rs, rowNum) -> mapLabContext(rs))
                .optional();
    }

    public java.util.List<Map<String, Object>> findByRegisterId(Long registerId) {
        return jdbcClient.sql("""
                        SELECT ir.id, ir.register_id, ir.patient_id, ir.medical_technology_id, ir.doctor_id,
                               ir.item_price, ir.purpose, ir.body_part, ir.remark, ir.status,
                               ir.result_text, ir.result_time, mt.item_name
                        FROM inspection_request ir
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        WHERE ir.register_id = :registerId AND ir.delmark = 0
                        ORDER BY ir.create_time ASC
                        """)
                .param("registerId", registerId)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    /** 患者端：已出结果的检验报告列表 */
    public java.util.List<Map<String, Object>> findResultsByPatient(Long patientId) {
        return jdbcClient.sql("""
                        SELECT ir.id, ir.register_id, ir.patient_id, ir.status,
                               ir.purpose, ir.body_part,
                               ir.result_text, ir.result_time, mt.item_name
                        FROM inspection_request ir
                        JOIN medical_technology mt ON ir.medical_technology_id = mt.id
                        WHERE ir.patient_id = :patientId
                          AND ir.status >= :resultReady
                          AND ir.delmark = 0
                        ORDER BY ir.result_time DESC NULLS LAST, ir.update_time DESC
                        """)
                .param("patientId", patientId)
                .param("resultReady", com.hospital.common.constant.InspectionRequestStatus.RESULT_READY)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("inspectionRequestId", rs.getLong("id"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("status", rs.getInt("status"));
                    row.put("resultText", rs.getString("result_text"));
                    row.put("resultTime", rs.getObject("result_time", OffsetDateTime.class));
                    row.put("itemName", rs.getString("item_name"));
                    row.put("purpose", rs.getString("purpose"));
                    row.put("bodyPart", rs.getString("body_part"));
                    return row;
                })
                .list();
    }

    private Map<String, Object> mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("inspectionRequestId", rs.getLong("id"));
        row.put("registerId", rs.getLong("register_id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("medicalTechnologyId", rs.getLong("medical_technology_id"));
        row.put("doctorId", rs.getLong("doctor_id"));
        row.put("itemPrice", rs.getBigDecimal("item_price"));
        row.put("purpose", rs.getString("purpose"));
        row.put("bodyPart", rs.getString("body_part"));
        row.put("remark", rs.getString("remark"));
        row.put("status", rs.getInt("status"));
        row.put("resultText", rs.getString("result_text"));
        row.put("resultTime", rs.getObject("result_time", OffsetDateTime.class));
        row.put("itemName", rs.getString("item_name"));
        return row;
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
        row.put("orderingDoctorName", rs.getString("ordering_doctor_name"));
        return row;
    }
}
