package com.hospital.auth.service;

import com.hospital.auth.config.AuthProperties;
import com.hospital.auth.dto.PatientTokenRequest;
import com.hospital.auth.dto.PatientTokenResponse;
import com.hospital.auth.repository.PatientRepository;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PatientTokenService {

    private final PatientRepository patientRepository;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties authProperties;

    public PatientTokenResponse issuePatientToken(PatientTokenRequest request, HttpServletRequest httpRequest) {
        assertInternalCaller(httpRequest);

        if (!patientRepository.existsById(request.getPatientId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "患者不存在");
        }

        String medicalRecordNo = request.getMedicalRecordNo();
        if (!StringUtils.hasText(medicalRecordNo)) {
            medicalRecordNo = patientRepository.findMedicalRecordNo(request.getPatientId());
        }

        String accessToken = jwtTokenService.createPatientAccessToken(
                request.getPatientId(), medicalRecordNo);

        return PatientTokenResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtTokenService.getAccessExpireSeconds())
                .tokenType("Bearer")
                .build();
    }

    private void assertInternalCaller(HttpServletRequest request) {
        String headerName = authProperties.getInternal().getHeader();
        String caller = request.getHeader(headerName);
        if (!StringUtils.hasText(caller)
                || !authProperties.getInternal().getAllowedServices().contains(caller)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权调用内部签发接口");
        }
    }
}
