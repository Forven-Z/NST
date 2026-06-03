package com.hospital.auth.service;

import com.hospital.auth.config.AuthProperties;
import com.hospital.auth.domain.StaffAccount;
import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.UserType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long accessExpireSeconds;
    private final long refreshExpireSeconds;

    public JwtTokenService(AuthProperties authProperties) {
        byte[] keyBytes = authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpireSeconds = authProperties.getJwt().getAccessTokenExpireSeconds();
        this.refreshExpireSeconds = authProperties.getJwt().getRefreshTokenExpireSeconds();
    }

    public String createStaffAccessToken(StaffAccount account, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(account.userId()))
                .claim(JwtClaims.TYPE, UserType.STAFF.name())
                .claim(JwtClaims.TOKEN_KIND, JwtClaims.TOKEN_KIND_ACCESS)
                .claim(JwtClaims.USER_ID, account.userId())
                .claim(JwtClaims.EMPLOYEE_ID, account.employeeId())
                .claim(JwtClaims.ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public String createStaffRefreshToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(JwtClaims.TYPE, UserType.STAFF.name())
                .claim(JwtClaims.TOKEN_KIND, JwtClaims.TOKEN_KIND_REFRESH)
                .claim(JwtClaims.USER_ID, userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshExpireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    public String createPatientAccessToken(Long patientId, String medicalRecordNo) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(patientId))
                .claim(JwtClaims.TYPE, UserType.PATIENT.name())
                .claim(JwtClaims.TOKEN_KIND, JwtClaims.TOKEN_KIND_ACCESS)
                .claim(JwtClaims.PATIENT_ID, patientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpireSeconds)))
                .signWith(secretKey);
        if (medicalRecordNo != null && !medicalRecordNo.isBlank()) {
            builder.claim(JwtClaims.MEDICAL_RECORD_NO, medicalRecordNo);
        }
        return builder.compact();
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 无效或已过期");
        }
    }

    public long getAccessExpireSeconds() {
        return accessExpireSeconds;
    }
}
