package com.hospital.auth.repository;

import com.hospital.auth.domain.StaffAccount;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class StaffAuthRepository {

    private static final String FIND_BY_USERNAME = """
            SELECT u.id            AS user_id,
                   u.username,
                   u.password_hash,
                   u.user_type,
                   u.status,
                   e.id            AS employee_id,
                   e.real_name,
                   e.role_type,
                   e.dept_id,
                   d.dept_name
            FROM sys_user u
            LEFT JOIN employee e ON u.employee_id = e.id AND e.delmark = 0
            LEFT JOIN department d ON e.dept_id = d.id AND d.delmark = 0
            WHERE u.username = :username
              AND u.delmark = 0
            """;

    private static final String FIND_BY_USER_ID = """
            SELECT u.id            AS user_id,
                   u.username,
                   u.password_hash,
                   u.user_type,
                   u.status,
                   e.id            AS employee_id,
                   e.real_name,
                   e.role_type,
                   e.dept_id,
                   d.dept_name
            FROM sys_user u
            LEFT JOIN employee e ON u.employee_id = e.id AND e.delmark = 0
            LEFT JOIN department d ON e.dept_id = d.id AND d.delmark = 0
            WHERE u.id = :userId
              AND u.delmark = 0
            """;

    private final JdbcClient jdbcClient;

    public StaffAuthRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<StaffAccount> findByUsername(String username) {
        return jdbcClient.sql(FIND_BY_USERNAME)
                .param("username", username)
                .query(StaffAccount.class)
                .optional();
    }

    public Optional<StaffAccount> findByUserId(Long userId) {
        return jdbcClient.sql(FIND_BY_USER_ID)
                .param("userId", userId)
                .query(StaffAccount.class)
                .optional();
    }
}
