package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
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
}
