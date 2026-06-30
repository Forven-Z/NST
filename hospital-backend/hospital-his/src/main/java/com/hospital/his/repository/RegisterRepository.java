package com.hospital.his.repository;

import com.hospital.common.constant.RegisterChannel;
import com.hospital.common.constant.VisitState;
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
        return insertRegister(patientId, schedulingId, deptId, employeeId, registLevelId,
                settleCategoryId, visitDate, noonType, visitState, registFee,
                RegisterChannel.ONLINE, null);
    }

    /** 同日、同医生、同午别是否存在未结束的有效挂号（待支付/已挂号/接诊中） */
    public boolean existsActiveRegister(Long patientId, Long employeeId, LocalDate visitDate, int noonType) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)::int
                        FROM register
                        WHERE delmark = 0
                          AND patient_id = :patientId
                          AND employee_id = :employeeId
                          AND visit_date = :visitDate
                          AND noon_type = :noonType
                          AND visit_state IN (0, 1, 2)
                        """)
                .param("patientId", patientId)
                .param("employeeId", employeeId)
                .param("visitDate", visitDate)
                .param("noonType", noonType)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    public long insertRegister(Long patientId, Long schedulingId, Long deptId, Long employeeId,
                               Long registLevelId, Long settleCategoryId, LocalDate visitDate,
                               int noonType, int visitState, BigDecimal registFee,
                               String channel, Long registrarId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO register (patient_id, scheduling_id, dept_id, employee_id, regist_level_id,
                                              settle_category_id, visit_date, noon_type, visit_state, channel,
                                              regist_fee, registrar_id)
                        VALUES (:patientId, :schedulingId, :deptId, :employeeId, :registLevelId,
                                :settleCategoryId, :visitDate, :noonType, :visitState, :channel,
                                :registFee, :registrarId)
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
                .param("channel", channel)
                .param("registFee", registFee)
                .param("registrarId", registrarId)
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

    /** 乐观锁式状态迁移；返回受影响行数（0 表示当前状态与 expectedFrom 不一致）。 */
    public int updateVisitStateIfCurrent(Long registerId, int expectedFrom, int newState) {
        return jdbcClient.sql("""
                        UPDATE register SET visit_state = :newState, update_time = NOW()
                        WHERE id = :id AND visit_state = :expectedFrom
                        """)
                .param("id", registerId)
                .param("expectedFrom", expectedFrom)
                .param("newState", newState)
                .update();
    }

    public void markCalled(Long registerId) {
        markCalledIfCurrent(registerId, VisitState.REGISTERED);
    }

    /** 叫号：仅当当前为 expectedFrom（通常为已挂号）时迁移为接诊中。 */
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

    public void markFinished(Long registerId) {
        markFinishedIfCurrent(registerId, VisitState.IN_CONSULTATION);
    }

    /** 结束看诊：仅当当前为接诊中时迁移为看诊结束。 */
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

    /** @deprecated 请使用 {@link com.hospital.his.visit.VisitLifecycleCoordinator#autoDayClose} */
    @Deprecated
    public void markAutoDayClosed(Long registerId, String remark) {
        if (markAutoDayClosedIfCurrent(registerId, VisitState.REGISTERED, remark) == 0) {
            markAutoDayClosedIfCurrent(registerId, VisitState.IN_CONSULTATION, remark);
        }
    }

    /** 日终关单：仅当当前为 expectedFrom 时迁移为看诊结束并写入 remark。 */
    public int markAutoDayClosedIfCurrent(Long registerId, int expectedFrom, String remark) {
        return jdbcClient.sql("""
                        UPDATE register
                        SET visit_state = 3,
                            visit_end_time = :now,
                            remark = :remark,
                            update_time = NOW()
                        WHERE id = :id AND visit_state = :expectedFrom
                        """)
                .param("id", registerId)
                .param("expectedFrom", expectedFrom)
                .param("now", OffsetDateTime.now())
                .param("remark", remark)
                .update();
    }

    public List<Long> findIdsPendingPaymentExpired(OffsetDateTime cutoff) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM register
                        WHERE delmark = 0
                          AND visit_state = 0
                          AND create_time < :cutoff
                        ORDER BY id
                        """)
                .param("cutoff", cutoff)
                .query(Long.class)
                .list();
    }

    /** visit_state 1/2 且 visit_date 已到期终关单（早于今天，或今天且当前时刻 >= 21:00）。 */
    public List<Long> findIdsDueForDayClose(LocalDate today, java.time.LocalTime dayCloseTime) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM register
                        WHERE delmark = 0
                          AND visit_state IN (1, 2)
                          AND (
                              visit_date < :today
                              OR (visit_date = :today AND CURRENT_TIME >= :dayCloseTime)
                          )
                        ORDER BY id
                        """)
                .param("today", today)
                .param("dayCloseTime", dayCloseTime)
                .query(Long.class)
                .list();
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

    public List<Map<String, Object>> findByOwnerPatientId(Long ownerPatientId, Integer visitState, int offset, int limit) {
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
        row.put("callTime", rs.getObject("call_time", OffsetDateTime.class));
        row.put("remark", rs.getString("remark"));
        row.put("registTime", rs.getObject("regist_time", OffsetDateTime.class));
        row.put("createTime", rs.getObject("regist_time", OffsetDateTime.class));
        row.put("deptName", rs.getString("dept_name"));
        row.put("doctorName", rs.getString("doctor_name"));
        row.put("registLevelName", rs.getString("regist_level_name"));
        row.put("patientName", rs.getString("patient_name"));
        row.put("medicalRecordNo", rs.getString("medical_record_no"));
        Integer mrStatus = readOptionalColumnInt(rs, "medical_record_status");
        row.put("medicalRecordStatus", mrStatus);
        row.put("hasMedicalRecord", mrStatus != null && mrStatus == 2);
        return row;
    }

    private Integer readOptionalColumnInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (column.equalsIgnoreCase(meta.getColumnLabel(i))) {
                Object val = rs.getObject(i);
                return val != null ? ((Number) val).intValue() : null;
            }
        }
        return null;
    }

    /** 方案 A：仅返回指定就诊人的挂号，且操作者须为本人或 link 授权 */
    public List<Map<String, Object>> findByVisitPatientForOperator(Long operatorPatientId, Long visitPatientId,
                                                                   Integer visitState, int offset, int limit) {
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
                               mr.status AS medical_record_status
                        FROM register r
                        JOIN patient p ON r.patient_id = p.id
                        JOIN department d ON r.dept_id = d.id
                        LEFT JOIN employee e ON r.employee_id = e.id
                        JOIN regist_level rl ON r.regist_level_id = rl.id
                        LEFT JOIN medical_record mr ON mr.register_id = r.id AND mr.delmark = 0
                        WHERE r.delmark = 0
                          AND r.patient_id = :visitPatientId
                          AND (
                              :visitPatientId = :operatorId
                              OR EXISTS (
                                  SELECT 1 FROM patient_family_link l
                                  WHERE l.owner_patient_id = :operatorId
                                    AND l.member_patient_id = :visitPatientId
                                    AND l.delmark = 0
                              )
                          )
                          AND (CAST(:visitState AS INTEGER) IS NULL OR r.visit_state = CAST(:visitState AS INTEGER))
                        ORDER BY r.create_time DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("operatorId", operatorPatientId)
                .param("visitPatientId", visitPatientId)
                .param("visitState", visitState)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> mapRegisterRow(rs))
                .list();
    }

    /**
     * 医生查阅患者既往就诊：按 patient_id 列出有效门诊记录。
     */
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

    /**
     * 患者就诊记录列表：有效门诊（已挂号/接诊中/看诊结束），含医嘱与报告计数。
     */
    public List<Map<String, Object>> findVisitSummariesForOperator(Long operatorPatientId, Long visitPatientId,
                                                                    int offset, int limit) {
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
                          AND r.patient_id = :visitPatientId
                          AND (
                              :visitPatientId = :operatorId
                              OR EXISTS (
                                  SELECT 1 FROM patient_family_link l
                                  WHERE l.owner_patient_id = :operatorId
                                    AND l.member_patient_id = :visitPatientId
                                    AND l.delmark = 0
                              )
                          )
                        ORDER BY r.visit_date DESC NULLS LAST, r.create_time DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("operatorId", operatorPatientId)
                .param("visitPatientId", visitPatientId)
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

    /** 收费窗口：按患者查挂号记录（可选 visitState 过滤） */
    public List<Map<String, Object>> findByPatientIdForRegistrar(Long patientId, Integer visitState, int limit) {
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
                        WHERE r.delmark = 0
                          AND r.patient_id = :patientId
                          AND (CAST(:visitState AS INTEGER) IS NULL OR r.visit_state = CAST(:visitState AS INTEGER))
                        ORDER BY r.create_time DESC
                        LIMIT :limit
                        """)
                .param("patientId", patientId)
                .param("visitState", visitState)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRegisterRow(rs))
                .list();
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
