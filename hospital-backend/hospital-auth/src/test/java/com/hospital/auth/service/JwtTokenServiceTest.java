package com.hospital.auth.service;

import com.hospital.auth.config.AuthProperties;
import com.hospital.auth.domain.StaffAccount;
import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.UserType;
import com.hospital.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String TEST_SECRET =
            "hospital-dev-secret-key-change-in-production-min-32-chars!!";

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret(TEST_SECRET);
        properties.getJwt().setAccessTokenExpireSeconds(7200);
        properties.getJwt().setRefreshTokenExpireSeconds(604800);
        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void createStaffAccessToken_containsExpectedClaims() {
        StaffAccount account = new StaffAccount(
                1L, "doctor01", "hash", "STAFF", 1,
                101L, "张医生", "DOCTOR", 10L, "内科");

        String token = jwtTokenService.createStaffAccessToken(account, List.of("DOCTOR"));
        Claims claims = jwtTokenService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo(UserType.STAFF.name());
        assertThat(claims.get(JwtClaims.TOKEN_KIND, String.class)).isEqualTo(JwtClaims.TOKEN_KIND_ACCESS);
        assertThat(claims.get(JwtClaims.USER_ID, Number.class).longValue()).isEqualTo(1L);
        assertThat(claims.get(JwtClaims.EMPLOYEE_ID, Number.class).longValue()).isEqualTo(101L);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(JwtClaims.ROLES, List.class);
        assertThat(roles).containsExactly("DOCTOR");
    }

    @Test
    void createStaffRefreshToken_containsRefreshKind() {
        String token = jwtTokenService.createStaffRefreshToken(99L);
        Claims claims = jwtTokenService.parseClaims(token);

        assertThat(claims.get(JwtClaims.TOKEN_KIND, String.class)).isEqualTo(JwtClaims.TOKEN_KIND_REFRESH);
        assertThat(claims.get(JwtClaims.USER_ID, Number.class).longValue()).isEqualTo(99L);
    }

    @Test
    void createPatientAccessToken_includesMedicalRecordNo() {
        String token = jwtTokenService.createPatientAccessToken(200L, "MR202606040100");
        Claims claims = jwtTokenService.parseClaims(token);

        assertThat(claims.get(JwtClaims.TYPE, String.class)).isEqualTo(UserType.PATIENT.name());
        assertThat(claims.get(JwtClaims.PATIENT_ID, Number.class).longValue()).isEqualTo(200L);
        assertThat(claims.get(JwtClaims.MEDICAL_RECORD_NO, String.class)).isEqualTo("MR202606040100");
    }

    @Test
    void createPatientAccessToken_omitsBlankMedicalRecordNo() {
        String token = jwtTokenService.createPatientAccessToken(200L, "  ");
        Claims claims = jwtTokenService.parseClaims(token);

        assertThat(claims.get(JwtClaims.MEDICAL_RECORD_NO)).isNull();
    }

    @Test
    void parseClaims_rejectsInvalidToken() {
        assertThatThrownBy(() -> jwtTokenService.parseClaims("invalid.token.value"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Token 无效");
    }

    @Test
    void getAccessExpireSeconds() {
        assertThat(jwtTokenService.getAccessExpireSeconds()).isEqualTo(7200L);
    }
}
