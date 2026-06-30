package com.hospital.disposal.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EmployeeRepository {

    private final JdbcClient jdbcClient;

    public EmployeeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<String> findRealNameById(Long employeeId) {
        if (employeeId == null) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        SELECT real_name FROM employee
                        WHERE id = :id AND delmark = 0
                        """)
                .param("id", employeeId)
                .query(String.class)
                .optional();
    }
}
