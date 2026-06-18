package com.hospital.aibridge.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AiRegisterRepository {

    private final JdbcClient jdbcClient;

    public AiRegisterRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Long> findPatientIdByRegisterId(Long registerId) {
        if (registerId == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        SELECT patient_id
                        FROM register
                        WHERE id = :registerId
                          AND delmark = 0
                        """)
                .param("registerId", registerId)
                .query(Long.class)
                .optional();
    }
}
