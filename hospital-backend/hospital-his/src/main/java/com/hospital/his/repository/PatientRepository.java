package com.hospital.his.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PatientRepository {

    private final JdbcClient jdbcClient;

    public PatientRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public String findMedicalRecordNo(Long patientId) {
        return jdbcClient.sql("SELECT medical_record_no FROM patient WHERE id = :id")
                .param("id", patientId)
                .query(String.class)
                .optional()
                .orElse(null);
    }
}