package com.hospital.pacs.repository;

import com.hospital.common.execute.MedTechOrderStatusWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CheckRequestRepository implements MedTechOrderStatusWriter {

    private final JdbcClient jdbcClient;

    public CheckRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> findQueue(Integer status, Long executorId, int offset, int limit) {
        return jdbcClient.sql("""
                        WITH executor_load AS (
                            SELECT executor_id, COUNT(*)::int AS load_count
                            FROM check_request
                            WHERE delmark = 0
                              AND status IN (20, 30)
                              AND executor_id IS NOT NULL
                            GROUP BY executor_id
                        )
                        SELECT cr.id AS check_request_id,
                               cr.register_id,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               mt.item_name,
                               cr.item_price,
                               cr.status,
                               cr.order_time,
                               cr.executor_id,
                               exe.real_name AS executor_name,
                               COALESCE(el.load_count, 0) AS executor_load
                        FROM check_request cr
                        JOIN patient p ON cr.patient_id = p.id
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        LEFT JOIN employee exe ON cr.executor_id = exe.id
                        LEFT JOIN executor_load el ON cr.executor_id = el.executor_id
                        WHERE cr.delmark = 0
                          AND (CAST(:status AS INTEGER) IS NULL OR cr.status = CAST(:status AS INTEGER))
                          AND (CAST(:executorId AS BIGINT) IS NULL OR cr.executor_id = CAST(:executorId AS BIGINT))
                        ORDER BY cr.order_time
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status)
                .param("executorId", executorId)
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
                    row.put("executorId", rs.getObject("executor_id") != null ? rs.getLong("executor_id") : null);
                    row.put("executorName", rs.getString("executor_name"));
                    row.put("executorLoad", rs.getInt("executor_load"));
                    return row;
                })
                .list();
    }

    public List<Long> findUnassignedPaidIdsForUpdate() {
        return jdbcClient.sql("""
                        SELECT id
                        FROM check_request
                        WHERE delmark = 0
                          AND status = 20
                          AND executor_id IS NULL
                        ORDER BY order_time, id
                        FOR UPDATE SKIP LOCKED
                        """)
                .query(Long.class)
                .list();
    }

    public List<Map<String, Object>> findDoctorLoads(String roleType) {
        return jdbcClient.sql("""
                        SELECT e.id AS employee_id,
                               e.real_name AS employee_name,
                               COALESCE(COUNT(cr.id), 0)::int AS load_count
                        FROM employee e
                        LEFT JOIN check_request cr
                          ON cr.executor_id = e.id
                         AND cr.delmark = 0
                         AND cr.status IN (20, 30)
                        WHERE e.delmark = 0
                          AND e.role_type = :roleType
                        GROUP BY e.id, e.real_name
                        ORDER BY load_count ASC, e.id ASC
                        """)
                .param("roleType", roleType)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("employeeName", rs.getString("employee_name"));
                    row.put("loadCount", rs.getInt("load_count"));
                    return row;
                })
                .list();
    }

    public void assignExecutorIfUnassigned(Long id, Long executorId) {
        jdbcClient.sql("""
                        UPDATE check_request
                        SET executor_id = :executorId, update_time = NOW()
                        WHERE id = :id
                          AND status = 20
                          AND executor_id IS NULL
                          AND delmark = 0
                        """)
                .param("id", id)
                .param("executorId", executorId)
                .update();
    }

    public Optional<Map<String, Object>> findReportContext(Long id) {
        return jdbcClient.sql("""
                        SELECT cr.id AS check_request_id,
                               cr.register_id,
                               cr.patient_id,
                               cr.status,
                               cr.purpose,
                               cr.body_part,
                               cr.remark AS order_remark,
                               cr.result_text,
                               cr.result_time,
                               cr.execute_time,
                               cr.result_input_id,
                               cr.reviewer_id,
                               mt.item_name,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               p.gender,
                               p.age,
                               d.dept_name AS department_name,
                               mr.diagnosis AS clinical_diagnosis,
                               exe.real_name AS executor_name,
                               e1.real_name AS reporter_name,
                               e2.real_name AS reviewer_name,
                               doc.real_name AS ordering_doctor_name
                        FROM check_request cr
                        JOIN patient p ON cr.patient_id = p.id
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        JOIN register reg ON cr.register_id = reg.id
                        JOIN department d ON reg.dept_id = d.id
                        LEFT JOIN medical_record mr ON mr.register_id = cr.register_id AND mr.delmark = 0
                        LEFT JOIN employee exe ON cr.executor_id = exe.id
                        LEFT JOIN employee e1 ON cr.result_input_id = e1.id
                        LEFT JOIN employee e2 ON cr.reviewer_id = e2.id
                        LEFT JOIN employee doc ON cr.doctor_id = doc.id
                        WHERE cr.id = :id AND cr.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapReportContext(rs))
                .optional();
    }

    public Optional<Map<String, Object>> findDetail(Long id) {
        return jdbcClient.sql("""
                        SELECT cr.id,
                               cr.register_id,
                               cr.patient_id,
                               cr.status,
                               cr.purpose,
                               cr.body_part,
                               cr.remark,
                               cr.result_text,
                               cr.result_time,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               mt.item_name
                        FROM check_request cr
                        JOIN patient p ON cr.patient_id = p.id
                        JOIN medical_technology mt ON cr.medical_technology_id = mt.id
                        WHERE cr.id = :id AND cr.delmark = 0
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
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
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("itemName", rs.getString("item_name"));
                    return row;
                })
                .optional();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long id) {
        return jdbcClient.sql("""
                        SELECT id, status, executor_id FROM check_request
                        WHERE id = :id AND delmark = 0 FOR UPDATE
                        """)
                .param("id", id)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("status", rs.getInt("status"));
                    row.put("executorId", rs.getObject("executor_id") != null ? rs.getLong("executor_id") : null);
                    return row;
                })
                .optional();
    }

    public void markExecuted(Long id, Long executorId) {
        markExecutedIfCurrent(id, com.hospital.common.constant.InspectionRequestStatus.PAID, executorId);
    }

    public int markExecutedIfCurrent(Long id, int expectedFrom, Long executorId) {
        return jdbcClient.sql("""
                        UPDATE check_request
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
                        UPDATE check_request SET status = :newStatus, update_time = NOW()
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
                            UPDATE check_request
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
                        UPDATE check_request
                        SET result_input_id = :resultInputId, reviewer_id = :reviewerId,
                            result_time = :now,
                            result_text = :resultText, result_attachment = NULL, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("resultInputId", resultInputId)
                .param("reviewerId", reviewerId)
                .param("now", OffsetDateTime.now())
                .param("resultText", resultText)
                .update();
    }

    private Map<String, Object> mapReportContext(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("checkRequestId", rs.getLong("check_request_id"));
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
        row.put("reporterName", rs.getString("reporter_name"));
        row.put("reviewerName", rs.getString("reviewer_name"));
        row.put("executorName", rs.getString("executor_name"));
        row.put("resultInputId", rs.getObject("result_input_id") != null ? rs.getLong("result_input_id") : null);
        row.put("reviewerId", rs.getObject("reviewer_id") != null ? rs.getLong("reviewer_id") : null);
        row.put("orderingDoctorName", rs.getString("ordering_doctor_name"));
        return row;
    }
}
