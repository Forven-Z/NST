package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RegisterRepository {

    private final JdbcClient jdbcClient;

    public RegisterRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertRegister(Long patientId, Long schedulingId, Long deptId, Long employeeId,
                               Long registLevelId, Long settleCategoryId, LocalDate visitDate,
                               int noonType, int visitState, BigDecimal registFee) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO register (patient_id, scheduling_id, dept_id, employee_id, regist_level_id,
                                              settle_category_id, visit_date, noon_type, visit_state, channel, regist_fee)
                        VALUES (:patientId, :schedulingId, :deptId, :employeeId, :registLevelId,
                                :settleCategoryId, :visitDate, :noonType, :visitState, 'ONLINE', :registFee)
                        """)
                .param("patientId", patientId)
                .param("schedulingId", schedulingId)
                .param("deptId", deptId)
                .param("employeeId", employeeId)
                .param("registLevelId", registLevelId)
                .param("settleCategoryId", settleCategoryId)
                .param("visitDate", visitDate)
                .param("noonType", noonType)
                .param("visitState", visitState)
                .param("registFee", registFee)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public void updateVisitState(Long registerId, int visitState) {
        jdbcClient.sql("""
                        UPDATE register SET visit_state = :visitState, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", registerId)
                .param("visitState", visitState)
                .update();
    }

    public void markCalled(Long registerId) {
        jdbcClient.sql("""
                        UPDATE register
                        SET visit_state = 2, call_time = :now, update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", registerId)
                .param("now", OffsetDateTime.now())
                .update();
    }

    public Optional<Map<String, Object>> findById(Long registerId) {
        return jdbcClient.sql("""
                        SELECT r.id, r.patient_id, r.employee_id, r.scheduling_id, r.visit_state, r.visit_date
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
                    return row;
                })
                .optional();
    }

    public Optional<Map<String, Object>> findByIdForUpdate(Long registerId) {
        return jdbcClient.sql("""
                        SELECT r.id, r.patient_id, r.employee_id, r.scheduling_id, r.visit_state
                        FROM register r
                        WHERE r.id = :id AND r.delmark = 0
                        FOR UPDATE
                        """)
                .param("id", registerId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("registerId", rs.getLong("id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("schedulingId", rs.getObject("scheduling_id", Long.class));
                    row.put("visitState", rs.getInt("visit_state"));
                    return row;
                })
                .optional();
    }

    public Optional<Map<String, Object>> findByIdAndPatientId(Long registerId, Long patientId) {
        return jdbcClient.sql("""
                        SELECT r.id, r.patient_id, r.employee_id, r.visit_state
                        FROM register r
                        WHERE r.id = :id AND r.patient_id = :patientId AND r.delmark = 0
                        """)
                .param("id", registerId)
                .param("patientId", patientId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("registerId", rs.getLong("id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("employeeId", rs.getLong("employee_id"));
                    row.put("visitState", rs.getInt("visit_state"));
                    return row;
                })
                .optional();
    }

    public List<Map<String, Object>> findDoctorQueue(Long employeeId, Integer visitState, String keyword,
                                                    int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT r.id AS register_id,
                               p.medical_record_no,
                               p.real_name AS patient_name,
                               p.gender,
                               p.birth_date,
                               r.visit_state,
                               r.create_time AS regist_time,
                               rl.level_name AS regist_level_name
                        FROM register r
                        JOIN patient p ON r.patient_id = p.id
                        JOIN regist_level rl ON r.regist_level_id = rl.id
                        WHERE r.delmark = 0
                          AND r.employee_id = :employeeId
                          AND r.visit_date = CURRENT_DATE
                          AND (CAST(:visitState AS INTEGER) IS NULL OR r.visit_state = CAST(:visitState AS INTEGER))
                          AND (
                              CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                              OR p.medical_record_no ILIKE :keywordPattern
                              OR p.real_name ILIKE :keywordPattern
                          )
                        ORDER BY r.create_time
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
                    row.put("medicalRecordNo", rs.getString("medical_record_no"));
                    row.put("patientName", rs.getString("patient_name"));
                    row.put("gender", rs.getObject("gender", Integer.class));
                    row.put("birthDate", rs.getObject("birth_date", LocalDate.class));
                    row.put("visitState", rs.getInt("visit_state"));
                    row.put("registTime", rs.getObject("regist_time", OffsetDateTime.class));
                    row.put("registLevelName", rs.getString("regist_level_name"));
                    return row;
                })
                .list();
    }

    public List<Map<String, Object>> findByOwnerPatientId(Long ownerPatientId, Integer visitState, int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT r.id AS register_id,
                               r.patient_id,
                               r.visit_state,
                               r.visit_date,
                               r.noon_type,
                               r.regist_fee,
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
                        WHERE r.delmark = 0
                          AND (
                              r.patient_id = :ownerId
                              OR r.patient_id IN (
                                  SELECT member_patient_id FROM patient_family_link
                                  WHERE owner_patient_id = :ownerId AND delmark = 0
                              )
                          )
                          AND (CAST(:visitState AS INTEGER) IS NULL OR r.visit_state = CAST(:visitState AS INTEGER))
                        ORDER BY r.create_time DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("ownerId", ownerPatientId)
                .param("visitState", visitState)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> mapRegisterRow(rs))
                .list();
    }

    public Optional<Map<String, Object>> findDetailForOwner(Long registerId, Long ownerPatientId) {
        return jdbcClient.sql("""
                        SELECT r.id AS register_id,
                               r.patient_id,
                               r.visit_state,
                               r.visit_date,
                               r.noon_type,
                               r.regist_fee,
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
                        WHERE r.id = :id AND r.delmark = 0
                          AND (
                              r.patient_id = :ownerId
                              OR r.patient_id IN (
                                  SELECT member_patient_id FROM patient_family_link
                                  WHERE owner_patient_id = :ownerId AND delmark = 0
                              )
                          )
                        """)
                .param("id", registerId)
                .param("ownerId", ownerPatientId)
                .query((rs, rowNum) -> mapRegisterRow(rs))
                .optional();
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
        row.put("registTime", rs.getObject("regist_time", OffsetDateTime.class));
        row.put("deptName", rs.getString("dept_name"));
        row.put("doctorName", rs.getString("doctor_name"));
        row.put("registLevelName", rs.getString("regist_level_name"));
        row.put("patientName", rs.getString("patient_name"));
        row.put("medicalRecordNo", rs.getString("medical_record_no"));
        return row;
    }

    public List<Map<String, Object>> findByPatientId(Long patientId, Integer visitState, int offset, int limit) {
        return findByOwnerPatientId(patientId, visitState, offset, limit);
    }

    public Optional<Map<String, Object>> findDetailByIdAndPatientId(Long registerId, Long patientId) {
        return findDetailForOwner(registerId, patientId);
    }

    public int countAheadInQueue(Long registerId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)::int
                        FROM register cur
                        JOIN register other ON other.delmark = 0
                            AND other.visit_date = cur.visit_date
                            AND other.dept_id = cur.dept_id
                            AND other.employee_id = cur.employee_id
                            AND other.noon_type = cur.noon_type
                            AND other.visit_state = 1
                            AND other.create_time < cur.create_time
                        WHERE cur.id = :registerId AND cur.delmark = 0
                        """)
                .param("registerId", registerId)
                .query(Integer.class)
                .single();
        return count != null ? count : 0;
    }
}
