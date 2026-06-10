package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MedicalRecordDiseaseRepository {

    private final JdbcClient jdbcClient;

    public MedicalRecordDiseaseRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Map<String, Object>> findByMedicalRecordId(Long medicalRecordId) {
        return jdbcClient.sql("""
                        SELECT disease_id, disease_type
                        FROM medical_record_disease
                        WHERE medical_record_id = :medicalRecordId
                        ORDER BY disease_type ASC, id ASC
                        """)
                .param("medicalRecordId", medicalRecordId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("diseaseId", rs.getLong("disease_id"));
                    row.put("diseaseType", rs.getInt("disease_type"));
                    return row;
                })
                .list();
    }

    public void replaceAll(Long medicalRecordId, List<Map<String, Object>> entries) {
        jdbcClient.sql("""
                        DELETE FROM medical_record_disease
                        WHERE medical_record_id = :medicalRecordId
                        """)
                .param("medicalRecordId", medicalRecordId)
                .update();

        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (Map<String, Object> entry : entries) {
            jdbcClient.sql("""
                            INSERT INTO medical_record_disease (medical_record_id, disease_id, disease_type)
                            VALUES (:medicalRecordId, :diseaseId, :diseaseType)
                            """)
                    .param("medicalRecordId", medicalRecordId)
                    .param("diseaseId", entry.get("diseaseId"))
                    .param("diseaseType", entry.get("diseaseType"))
                    .update();
        }
    }

    public static List<Long> toDiseaseIds(List<Map<String, Object>> entries) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            ids.add(((Number) entry.get("diseaseId")).longValue());
        }
        return ids;
    }
}
