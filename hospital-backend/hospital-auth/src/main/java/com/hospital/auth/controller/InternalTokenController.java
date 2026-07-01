package com.hospital.auth.controller;

import com.hospital.auth.dto.PatientTokenRequest;
import com.hospital.auth.dto.PatientTokenResponse;
import com.hospital.auth.service.PatientTokenService;
import com.hospital.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间内部接口，不经 Gateway 暴露；由 hospital-patient 通过 OpenFeign 直连调用。
 */
@RestController
@RequestMapping("/internal/token")
@RequiredArgsConstructor
public class InternalTokenController {

    private final PatientTokenService patientTokenService;

    @PostMapping("/patient")
    public Result<PatientTokenResponse> issuePatientToken(
            @Valid @RequestBody PatientTokenRequest request,
            HttpServletRequest httpRequest) {
        return Result.success(patientTokenService.issuePatientToken(request, httpRequest));
    }
}
