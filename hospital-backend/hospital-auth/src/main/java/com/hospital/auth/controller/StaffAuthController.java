package com.hospital.auth.controller;

import com.hospital.auth.dto.StaffLoginRequest;
import com.hospital.auth.dto.StaffLoginResponse;
import com.hospital.auth.dto.StaffMeResponse;
import com.hospital.auth.dto.TokenRefreshRequest;
import com.hospital.auth.dto.TokenRefreshResponse;
import com.hospital.auth.service.StaffAuthService;
import com.hospital.common.Result;
import com.hospital.common.auth.JwtClaims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    @PostMapping("/staff/login")
    public Result<StaffLoginResponse> login(@Valid @RequestBody StaffLoginRequest request) {
        return Result.success(staffAuthService.login(request));
    }

    @PostMapping("/token/refresh")
    public Result<TokenRefreshResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return Result.success(staffAuthService.refresh(request));
    }

    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of("service", "hospital-auth", "status", "UP"));
    }

    @GetMapping("/me")
    public Result<StaffMeResponse> me(
            @RequestHeader(value = JwtClaims.AUTHORIZATION_HEADER, required = false) String authorization) {
        return Result.success(staffAuthService.getCurrentStaff(authorization));
    }
}
