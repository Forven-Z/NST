package com.hospital.auth.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PatientRepository {

    private final JdbcClient jdbcClient;

    public PatientRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsById(Long patientId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(1)
                        FROM patient
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", patientId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    public String findMedicalRecordNo(Long patientId) {
        return jdbcClient.sql("""
                        SELECT medical_record_no
                        FROM patient
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", patientId)
                .query(String.class)
                .optional()
                .orElse(null);
    }
}
