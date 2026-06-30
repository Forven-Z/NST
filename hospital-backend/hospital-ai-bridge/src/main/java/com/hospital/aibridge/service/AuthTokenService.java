package com.hospital.aibridge.service;

import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.JwtTokenHelper;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class AuthTokenService {

    private final String jwtSecret;

    public AuthTokenService(
            @Value("${hospital.auth.jwt.secret:hospital-dev-secret-key-change-in-production-min-32-chars!!}")
            String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Optional<Long> employeeId(String authorization) {
        try {
            if (!StringUtils.hasText(authorization) || !authorization.startsWith(JwtClaims.BEARER_PREFIX)) {
                return Optional.empty();
            }
            String token = authorization.substring(JwtClaims.BEARER_PREFIX.length());
            Claims claims = JwtTokenHelper.parse(token, jwtSecret);
            return Optional.ofNullable(claims.get(JwtClaims.EMPLOYEE_ID, Long.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public Optional<Long> patientId(String authorization) {
        try {
            if (!StringUtils.hasText(authorization) || !authorization.startsWith(JwtClaims.BEARER_PREFIX)) {
                return Optional.empty();
            }
            String token = authorization.substring(JwtClaims.BEARER_PREFIX.length());
            Claims claims = JwtTokenHelper.parse(token, jwtSecret);
            return Optional.ofNullable(claims.get(JwtClaims.PATIENT_ID, Long.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
