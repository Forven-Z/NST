package com.hospital.auth.service;

import com.hospital.auth.domain.StaffAccount;
import com.hospital.auth.dto.StaffLoginRequest;
import com.hospital.auth.dto.StaffLoginResponse;
import com.hospital.auth.dto.StaffMeResponse;
import com.hospital.auth.dto.TokenRefreshRequest;
import com.hospital.auth.dto.TokenRefreshResponse;
import com.hospital.auth.repository.StaffAuthRepository;
import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.JwtTokenHelper;
import com.hospital.common.auth.UserType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffAuthService {

    private final StaffAuthRepository staffAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public StaffLoginResponse login(StaffLoginRequest request) {
        StaffAccount account = staffAuthRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        if (account.status() == null || account.status() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), account.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        if (!UserType.STAFF.name().equals(account.userType())
                && !UserType.ADMIN.name().equals(account.userType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该账号不允许员工端登录");
        }

        List<String> roles = resolveRoles(account);
        String accessToken = jwtTokenService.createStaffAccessToken(account, roles);
        String refreshToken = jwtTokenService.createStaffRefreshToken(account.userId());

        return StaffLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenService.getAccessExpireSeconds())
                .userId(account.userId())
                .employeeId(account.employeeId())
                .realName(account.realName())
                .roles(roles)
                .deptId(account.deptId())
                .deptName(account.deptName())
                .build();
    }

    public StaffMeResponse getCurrentStaff(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(JwtClaims.BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 Authorization Bearer Token");
        }
        String token = authorization.substring(JwtClaims.BEARER_PREFIX.length());
        Claims claims = jwtTokenService.parseClaims(token);
        if (!JwtTokenHelper.isAccessToken(claims)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请使用 accessToken 访问");
        }
        Long userId = claims.get(JwtClaims.USER_ID, Long.class);
        StaffAccount account = staffAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已失效"));
        return StaffMeResponse.builder()
                .userId(account.userId())
                .employeeId(account.employeeId())
                .realName(account.realName())
                .roles(resolveRoles(account))
                .deptId(account.deptId())
                .deptName(account.deptName())
                .build();
    }

    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        Claims claims = jwtTokenService.parseClaims(request.getRefreshToken());
        if (!JwtClaims.TOKEN_KIND_REFRESH.equals(claims.get(JwtClaims.TOKEN_KIND, String.class))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无效的 refreshToken");
        }
        if (!UserType.STAFF.name().equals(claims.get(JwtClaims.TYPE, String.class))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 类型不匹配");
        }

        Long userId = claims.get(JwtClaims.USER_ID, Long.class);
        StaffAccount account = staffAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已失效"));

        List<String> roles = resolveRoles(account);
        String accessToken = jwtTokenService.createStaffAccessToken(account, roles);
        String refreshToken = jwtTokenService.createStaffRefreshToken(account.userId());

        return TokenRefreshResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenService.getAccessExpireSeconds())
                .tokenType("Bearer")
                .build();
    }

    private List<String> resolveRoles(StaffAccount account) {
        if (account.roleType() != null && !account.roleType().isBlank()) {
            return List.of(account.roleType());
        }
        if (UserType.ADMIN.name().equals(account.userType())) {
            return List.of("ADMIN");
        }
        return List.of();
    }
}
