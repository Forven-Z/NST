package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MedicalRecordRepository {

    private final JdbcClient jdbcClient;

    public MedicalRecordRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Map<String, Object>> findByRegisterId(Long registerId) {
        return findByRegisterId(registerId, null);
    }

    /** @param patientVisibleOnly true 时仅返回 status=2（患者端） */
    public Optional<Map<String, Object>> findByRegisterId(Long registerId, Boolean patientVisibleOnly) {
        String statusClause = Boolean.TRUE.equals(patientVisibleOnly) ? " AND status = 2" : "";
        return jdbcClient.sql("""
                        SELECT id, register_id, patient_id, doctor_id,
                               readme, present, present_treat, history, allergy, physique,
                               diagnosis, cure, check_advice, inspection_advice, status
                        FROM medical_record
                        WHERE register_id = :registerId AND delmark = 0
                        """ + statusClause)
                .param("registerId", registerId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("patientId", rs.getLong("patient_id"));
                    row.put("doctorId", rs.getLong("doctor_id"));
                    row.put("readme", rs.getString("readme"));
                    row.put("present", rs.getString("present"));
                    row.put("presentTreat", rs.getString("present_treat"));
                    row.put("history", rs.getString("history"));
                    row.put("allergy", rs.getString("allergy"));
                    row.put("physique", rs.getString("physique"));
                    row.put("diagnosis", rs.getString("diagnosis"));
                    row.put("cure", rs.getString("cure"));
                    row.put("checkAdvice", rs.getString("check_advice"));
                    row.put("inspectionAdvice", rs.getString("inspection_advice"));
                    row.put("status", rs.getInt("status"));
                    return row;
                })
                .optional();
    }

    public long insert(Long registerId, Long patientId, Long doctorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO medical_record (register_id, patient_id, doctor_id)
                        VALUES (:registerId, :patientId, :doctorId)
                        """)
                .param("registerId", registerId)
                .param("patientId", patientId)
                .param("doctorId", doctorId)
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public void update(Long registerId, String readme, String present, String presentTreat, String history,
                       String allergy, String physique, String diagnosis, String cure,
                       String checkAdvice, String inspectionAdvice) {
        jdbcClient.sql("""
                        UPDATE medical_record
                        SET readme = :readme,
                            present = :present,
                            present_treat = :presentTreat,
                            history = :history,
                            allergy = :allergy,
                            physique = :physique,
                            diagnosis = :diagnosis,
                            cure = :cure,
                            check_advice = :checkAdvice,
                            inspection_advice = :inspectionAdvice,
                            update_time = NOW()
                        WHERE register_id = :registerId AND delmark = 0
                        """)
                .param("registerId", registerId)
                .param("readme", readme)
                .param("present", present)
                .param("presentTreat", presentTreat)
                .param("history", history)
                .param("allergy", allergy)
                .param("physique", physique)
                .param("diagnosis", diagnosis)
                .param("cure", cure)
                .param("checkAdvice", checkAdvice)
                .param("inspectionAdvice", inspectionAdvice)
                .update();
    }

    public Optional<Integer> findStatusByRegisterId(Long registerId) {
        return jdbcClient.sql("""
                        SELECT status FROM medical_record
                        WHERE register_id = :registerId AND delmark = 0
                        """)
                .param("registerId", registerId)
                .query(Integer.class)
                .optional();
    }

    public void updateStatus(Long registerId, int status) {
        jdbcClient.sql("""
                        UPDATE medical_record
                        SET status = :status, update_time = NOW()
                        WHERE register_id = :registerId AND delmark = 0
                        """)
                .param("registerId", registerId)
                .param("status", status)
                .update();
    }

    /** 患者端：已确诊提交（status=2）的病历摘要列表 */
    public List<Map<String, Object>> findSubmittedSummariesByPatientId(Long patientId) {
        return jdbcClient.sql("""
                        SELECT mr.register_id,
                               mr.diagnosis,
                               mr.readme,
                               mr.update_time AS record_time,
                               r.visit_date,
                               r.noon_type,
                               d.dept_name,
                               e.real_name AS doctor_name,
                               rl.level_name AS regist_level_name,
                               p.real_name AS patient_name
                        FROM medical_record mr
                        JOIN register r ON r.id = mr.register_id AND r.delmark = 0
                        JOIN patient p ON p.id = mr.patient_id
                        JOIN department d ON r.dept_id = d.id
                        LEFT JOIN employee e ON r.employee_id = e.id
                        JOIN regist_level rl ON r.regist_level_id = rl.id
                        WHERE mr.patient_id = :patientId
                          AND mr.status = 2
                          AND mr.delmark = 0
                        ORDER BY r.visit_date DESC NULLS LAST, mr.update_time DESC NULLS LAST
                        """)
                .param("patientId", patientId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("registerId", rs.getLong("register_id"));
                    row.put("diagnosis", rs.getString("diagnosis"));
                    row.put("readme", rs.getString("readme"));
                    row.put("recordTime", rs.getObject("record_time", OffsetDateTime.class));
                    row.put("visitDate", rs.getObject("visit_date", LocalDate.class));
                    int noonType = rs.getInt("noon_type");
                    row.put("noonType", noonType);
                    row.put("noonLabel", noonType == 1 ? "上午" : noonType == 2 ? "下午" : "—");
                    row.put("deptName", rs.getString("dept_name"));
                    row.put("doctorName", rs.getString("doctor_name"));
                    row.put("registLevelName", rs.getString("regist_level_name"));
                    row.put("patientName", rs.getString("patient_name"));
                    return row;
                })
                .list();
    }
}
