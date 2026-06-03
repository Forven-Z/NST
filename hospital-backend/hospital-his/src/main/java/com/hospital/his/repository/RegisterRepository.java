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
}
