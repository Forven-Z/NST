package com.hospital.auth.service;

import com.hospital.auth.config.AuthProperties;
import com.hospital.auth.domain.StaffAccount;
import com.hospital.auth.dto.StaffLoginRequest;
import com.hospital.auth.dto.TokenRefreshRequest;
import com.hospital.auth.repository.StaffAuthRepository;
import com.hospital.common.auth.UserType;
import com.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAuthServiceTest {

    private static final String TEST_SECRET =
            "hospital-dev-secret-key-change-in-production-min-32-chars!!";

    @Mock
    private StaffAuthRepository staffAuthRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private StaffAuthService staffAuthService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret(TEST_SECRET);
        JwtTokenService jwtTokenService = new JwtTokenService(properties);
        staffAuthService = new StaffAuthService(staffAuthRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    void login_rejectsUnknownUsername() {
        StaffLoginRequest request = new StaffLoginRequest();
        request.setUsername("unknown");
        request.setPassword("123456");
        when(staffAuthRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_rejectsDisabledAccount() {
        StaffLoginRequest request = new StaffLoginRequest();
        request.setUsername("doctor01");
        request.setPassword("123456");
        StaffAccount account = sampleAccount(0);
        when(staffAuthRepository.findByUsername("doctor01")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> staffAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已禁用");
    }

    @Test
    void login_rejectsWrongPassword() {
        StaffLoginRequest request = new StaffLoginRequest();
        request.setUsername("doctor01");
        request.setPassword("wrong");
        StaffAccount account = sampleAccount(1);
        when(staffAuthRepository.findByUsername("doctor01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", account.passwordHash())).thenReturn(false);

        assertThatThrownBy(() -> staffAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_rejectsPatientAccount() {
        StaffLoginRequest request = new StaffLoginRequest();
        request.setUsername("patient01");
        request.setPassword("123456");
        StaffAccount account = new StaffAccount(
                2L, "patient01", "hash", UserType.PATIENT.name(), 1,
                null, "患者", null, null, null);
        when(staffAuthRepository.findByUsername("patient01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        assertThatThrownBy(() -> staffAuthService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许员工端登录");
    }

    @Test
    void login_success() {
        StaffLoginRequest request = new StaffLoginRequest();
        request.setUsername("doctor01");
        request.setPassword("123456");
        StaffAccount account = sampleAccount(1);
        when(staffAuthRepository.findByUsername("doctor01")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("123456", account.passwordHash())).thenReturn(true);

        var response = staffAuthService.login(request);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getRoles()).containsExactly("DOCTOR");
    }

    @Test
    void getCurrentStaff_rejectsMissingBearer() {
        assertThatThrownBy(() -> staffAuthService.getCurrentStaff("Token abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Authorization Bearer");
    }

    @Test
    void refresh_rejectsAccessToken() {
        StaffAccount account = sampleAccount(1);
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret(TEST_SECRET);
        JwtTokenService jwtTokenService = new JwtTokenService(properties);
        String accessToken = jwtTokenService.createStaffAccessToken(account, List.of("DOCTOR"));
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setRefreshToken(accessToken);

        assertThatThrownBy(() -> staffAuthService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的 refreshToken");
    }

    @Test
    void refresh_success() {
        StaffAccount account = sampleAccount(1);
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret(TEST_SECRET);
        JwtTokenService jwtTokenService = new JwtTokenService(properties);
        when(staffAuthRepository.findByUserId(1L)).thenReturn(Optional.of(account));
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setRefreshToken(jwtTokenService.createStaffRefreshToken(1L));

        var response = staffAuthService.refresh(request);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        verify(staffAuthRepository).findByUserId(1L);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    private StaffAccount sampleAccount(int status) {
        return new StaffAccount(
                1L, "doctor01", "hash", UserType.STAFF.name(), status,
                101L, "张医生", "DOCTOR", 10L, "内科");
    }
}
