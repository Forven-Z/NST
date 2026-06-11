package com.hospital.auth.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class StaffAccountRepository {

    private final JdbcClient jdbcClient;

    public StaffAccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsByUsername(String username) {
        return Boolean.TRUE.equals(jdbcClient.sql(
                        "SELECT EXISTS(SELECT 1 FROM sys_user WHERE username = :u AND delmark = 0)")
                .param("u", username)
                .query(Boolean.class)
                .single());
    }

    public boolean existsByEmployeeId(Long employeeId) {
        return Boolean.TRUE.equals(jdbcClient.sql(
                        "SELECT EXISTS(SELECT 1 FROM sys_user WHERE employee_id = :eid AND delmark = 0)")
                .param("eid", employeeId)
                .query(Boolean.class)
                .single());
    }

    public Optional<String> findUsernameByEmployeeId(Long employeeId) {
        return jdbcClient.sql("SELECT username FROM sys_user WHERE employee_id = :eid AND delmark = 0")
                .param("eid", employeeId)
                .query(String.class)
                .optional();
    }

    public void insert(Long employeeId, String username, String passwordHash, String userType) {
        jdbcClient.sql("""
                INSERT INTO sys_user (username, password_hash, employee_id, user_type, status)
                VALUES (:username, :hash, :employeeId, :userType, 1)
                """)
                .param("username", username)
                .param("hash", passwordHash)
                .param("employeeId", employeeId)
                .param("userType", userType)
                .update();
    }

    public int update(Long employeeId, String username, String passwordHash, Integer status) {
        return jdbcClient.sql("""
                UPDATE sys_user SET
                    username = COALESCE(:username, username),
                    password_hash = COALESCE(:hash, password_hash),
                    status = COALESCE(:status, status),
                    update_time = NOW()
                WHERE employee_id = :employeeId AND delmark = 0
                """)
                .param("username", username)
                .param("hash", passwordHash)
                .param("status", status)
                .param("employeeId", employeeId)
                .update();
    }

    public int disableByEmployeeId(Long employeeId) {
        return jdbcClient.sql("""
                UPDATE sys_user SET status = 0, update_time = NOW()
                WHERE employee_id = :employeeId AND delmark = 0
                """)
                .param("employeeId", employeeId)
                .update();
    }
}
