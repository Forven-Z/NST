package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 临床域 {@code register} 读写：叫号/结束看诊、医生队列与就诊校验（ADR-019）。
 * 挂号创建/支付/退号等 patient 域方法见 hospital-patient 同名类。
 */
@Repository
public class RegisterRepository {

    private final JdbcClient jdbcClient;

    public RegisterRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Map<String, Object>> findById(Long registerId) {
        return jdbcClient.sql("""
                        SELECT r.id, r.patient_id, r.employee_id, r.scheduling_id, r.visit_state, r.visit_date,
                               r.regist_fee
                        FROM register r
                        WHERE r.id = :id AND r.delmark = 0
                        """)
                .param("id", registerId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("registerId", rs.getLong("id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("schedulingId", rs.getObject("scheduling_id", Long.class));
                    row.put("visitState", rs.getInt("visit_state"));
                    row.put("visitDate", rs.getObject("visit_date", LocalDate.class));
                    row.put("registFee", rs.getBigDecimal("regist_fee"));
                    return row;
                })
                .optional();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long registerId) {
        return jdbcClient.sql("""
                        SELECT r.id, r.patient_id, r.employee_id, r.scheduling_id, r.visit_state,
                               r.visit_date, r.call_time, r.create_time
                        FROM register r
                        WHERE r.id = :id AND r.delmark = 0
                        FOR UPDATE
                        """)
                .param("id", registerId)
                .query((rs, rowNum) -> mapRegisterCoreRow(rs))
                .optional();
    }

    public int markCalledIfCurrent(Long registerId, int expectedFrom) {
        return jdbcClient.sql("""
                        UPDATE register
                        SET visit_state = 2, call_time = :now, update_time = NOW()
                        WHERE id = :id AND visit_state = :expectedFrom
                        """)
                .param("id", registerId)
                .param("expectedFrom", expectedFrom)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public int markFinishedIfCurrent(Long registerId, int expectedFrom) {
        return jdbcClient.sql("""
                        UPDATE register
                        SET visit_state = 3, visit_end_time = :now, update_time = NOW()
                        WHERE id = :id AND visit_state = :expectedFrom
                        """)
                .param("id", registerId)
                .param("expectedFrom", expectedFrom)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public List<Map<String, Object>> findDoctorQueue(Long employeeId, Integer visitState, String keyword,
                                                    int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT register_id,
                               patient_id,
                               medical_record_no,
                               patient_name,
                               gender,
                               birth_date,
                               visit_state,
                               noon_type,
                               regist_time,
                               regist_level_name
                        FROM (
                            SELECT r.id AS register_id,
                                   r.patient_id,
                                   p.medical_record_no,
                                   p.real_name AS patient_name,
                                   p.gender,
                                   p.birth_date,
                                   r.visit_state,
                                   r.noon_type,
                                   r.create_time AS regist_time,
                                   rl.level_name AS regist_level_name,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY r.patient_id
                                       ORDER BY r.create_time ASC, r.id ASC
                                   ) AS rn
                            FROM register r
                            JOIN patient p ON r.patient_id = p.id
                            JOIN regist_level rl ON r.regist_level_id = rl.id
                            WHERE r.delmark = 0
                              AND r.employee_id = :employeeId
                              AND r.visit_date = CURRENT_DATE
                              AND (
                                  CAST(:visitState AS INTEGER) IS NOT NULL
                                      AND r.visit_state = CAST(:visitState AS INTEGER)
                                  OR CAST(:visitState AS INTEGER) IS NULL
                                      AND r.visit_state IN (1, 2)
                              )
                              AND (
                                  CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                                  OR p.medical_record_no ILIKE :keywordPattern
                                  OR p.real_name ILIKE :keywordPattern
                              )
                        ) q
                        WHERE rn = 1
                        ORDER BY noon_type ASC, regist_time ASC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("employeeId", employeeId)
                .param("visitState", visitState)
                .param("keyword", keyword)
                .param("keywordPattern", keyword == null || keyword.isBlank() ? null : "%" + keyword + "%")
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("gender", rs.getObject("gender", Integer.class));
                    row.put("birthDate", rs.getObject("birth_date", LocalDate.class));
                    row.put("visitState", rs.getInt("visit_state"));
                    int noonType = rs.getInt("noon_type");
                    row.put("noonType", noonType);
                    row.put("noonLabel", noonType == 1 ? "上午" : noonType == 2 ? "下午" : "—");
                    row.put("registTime", rs.getObject("regist_time", OffsetDateTime.class));
                    row.put("registLevelName", rs.getString("regist_level_name"));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> findVisitSummariesForPatient(Long patientId, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT r.id AS register_id,
                               r.patient_id,
                               r.visit_state,
                               r.visit_date,
                               r.noon_type,
                               r.regist_fee,
                               r.call_time,
                               r.remark,
                               r.create_time AS regist_time,
                               d.dept_name,
                               e.real_name AS doctor_name,
                               rl.level_name AS regist_level_name,
                               p.real_name AS patient_name,
                               p.medical_record_no,
                               mr.status AS medical_record_status,
                               mr.diagnosis,
                               mr.readme,
                               (
                                   (SELECT COUNT(*) FROM inspection_request ir
                                    WHERE ir.register_id = r.id AND ir.delmark = 0)
                                   + (SELECT COUNT(*) FROM check_request cr
                                      WHERE cr.register_id = r.id AND cr.delmark = 0)
                                   + (SELECT COUNT(*) FROM disposal_request dr
                                      WHERE dr.register_id = r.id AND dr.delmark = 0)
                                   + (SELECT COUNT(*) FROM prescription pr
                                      WHERE pr.register_id = r.id AND pr.delmark = 0)
                               )::int AS order_count,
                               (
                                   (SELECT COUNT(*) FROM inspection_request ir
                                    WHERE ir.register_id = r.id AND ir.delmark = 0 AND ir.status >= 40)
                                   + (SELECT COUNT(*) FROM check_request cr
                                      WHERE cr.register_id = r.id AND cr.delmark = 0 AND cr.status >= 40)
                                   + (SELECT COUNT(*) FROM disposal_request dr
                                      WHERE dr.register_id = r.id AND dr.delmark = 0 AND dr.status >= 40)
                               )::int AS report_ready_count
                        FROM register r
                        JOIN patient p ON r.patient_id = p.id
                        JOIN department d ON r.dept_id = d.id
                        LEFT JOIN employee e ON r.employee_id = e.id
                        JOIN regist_level rl ON r.regist_level_id = rl.id
                        LEFT JOIN medical_record mr ON mr.register_id = r.id AND mr.delmark = 0
                        WHERE r.delmark = 0
                          AND r.visit_state IN (1, 2, 3)
                          AND r.patient_id = :patientId
                        ORDER BY r.visit_date DESC NULLS LAST, r.create_time DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("patientId", patientId)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = mapRegisterRow(rs);
                    row.put("medicalRecordStatus", rs.getObject("medical_record_status", Integer.class));
                    row.put("orderCount", rs.getInt("order_count"));
                    row.put("reportReadyCount", rs.getInt("report_ready_count"));
                    row.put("diagnosis", rs.getString("diagnosis"));
                    row.put("readme", rs.getString("readme"));
                    return row;
                })
                .list();
    }

    public Optional<Map<String, Object>> findDetailByPatient(Long registerId, Long patientId) {
        return jdbcClient.sql("""
                        SELECT r.id AS register_id,
                               r.patient_id,
                               r.visit_state,
                               r.visit_date,
                               r.noon_type,
                               r.regist_fee,
                               r.call_time,
                               r.remark,
                               r.create_time AS regist_time,
                               d.dept_name,
                               e.real_name AS doctor_name,
                               rl.level_name AS regist_level_name,
                               p.real_name AS patient_name,
                               p.medical_record_no
                        FROM register r
                        JOIN patient p ON r.patient_id = p.id
                        JOIN department d ON r.dept_id = d.id
                        LEFT JOIN employee e ON r.employee_id = e.id
                        JOIN regist_level rl ON r.regist_level_id = rl.id
                        WHERE r.id = :id AND r.delmark = 0 AND r.patient_id = :patientId
                        """)
                .param("id", registerId)
                .param("patientId", patientId)
                .query((rs, rowNum) -> mapRegisterRow(rs))
                .optional();
    }

    private Map<String, Object> mapRegisterCoreRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("registerId", rs.getLong("id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("employeeId", rs.getLong("employee_id"));
        row.put("schedulingId", rs.getObject("scheduling_id", Long.class));
        row.put("visitState", rs.getInt("visit_state"));
        row.put("visitDate", rs.getObject("visit_date", LocalDate.class));
        row.put("callTime", rs.getObject("call_time", OffsetDateTime.class));
        row.put("createTime", rs.getObject("create_time", OffsetDateTime.class));
        return row;
    }

    private Map<String, Object> mapRegisterRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("registerId", rs.getLong("register_id"));
        row.put("patientId", rs.getLong("patient_id"));
        row.put("visitState", rs.getInt("visit_state"));
        row.put("workDate", rs.getObject("visit_date", LocalDate.class));
        row.put("noonType", rs.getInt("noon_type"));
        row.put("noonLabel", rs.getInt("noon_type") == 1 ? "上午" : "下午");
        row.put("registFee", rs.getBigDecimal("regist_fee"));
        row.put("callTime", rs.getObject("call_time", OffsetDateTime.class));
        row.put("remark", rs.getString("remark"));
        row.put("registTime", rs.getObject("regist_time", OffsetDateTime.class));
        row.put("createTime", rs.getObject("regist_time", OffsetDateTime.class));
        row.put("deptName", rs.getString("dept_name"));
        row.put("doctorName", rs.getString("doctor_name"));
        row.put("registLevelName", rs.getString("regist_level_name"));
        row.put("patientName", rs.getString("patient_name"));
        row.put("medicalRecordNo", rs.getString("medical_record_no"));
        return row;
    }
}
