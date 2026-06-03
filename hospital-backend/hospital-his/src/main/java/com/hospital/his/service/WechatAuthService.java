package com.hospital.his.service;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.client.AuthTokenFeignClient;
import com.hospital.his.client.dto.PatientTokenFeignRequest;
import com.hospital.his.client.dto.PatientTokenFeignResponse;
import com.hospital.his.config.HisProperties;
import com.hospital.his.dto.patient.WechatLoginRequest;
import com.hospital.his.dto.patient.WechatLoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatAuthService {

    private final PatientWechatService patientWechatService;
    private final AuthTokenFeignClient authTokenFeignClient;
    private final HisProperties hisProperties;

    public WechatLoginResponse login(WechatLoginRequest request) {
        String openid = resolveOpenid(request.getCode());
        PatientSession session = patientWechatService.upsertSession(openid, request);
        return issueTokenFromAuth(session);
    }

    private WechatLoginResponse issueTokenFromAuth(PatientSession session) {
        PatientTokenFeignRequest tokenRequest = new PatientTokenFeignRequest();
        tokenRequest.setPatientId(session.patientId());
        tokenRequest.setMedicalRecordNo(session.medicalRecordNo());

        log.info("Feign 调用 auth 签发患者 Token, patientId={}", session.patientId());
        Result<PatientTokenFeignResponse> tokenResult = authTokenFeignClient.issuePatientToken(tokenRequest);
        if (tokenResult == null || !Boolean.TRUE.equals(tokenResult.getSuccess()) || tokenResult.getData() == null) {
            String message = tokenResult != null ? tokenResult.getMessage() : "auth 无响应";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Token 签发失败: " + message);
        }

        PatientTokenFeignResponse tokenData = tokenResult.getData();
        return WechatLoginResponse.builder()
                .accessToken(tokenData.getAccessToken())
                .expiresIn(tokenData.getExpiresIn())
                .patientId(session.patientId())
                .medicalRecordNo(session.medicalRecordNo())
                .isNewPatient(session.isNewPatient())
                .build();
    }

    private String resolveOpenid(String code) {
        if (hisProperties.getWechat().isMockEnabled()) {
            return "mock_openid_" + sha256(code);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "微信登录未配置，请开启 mock 模式");
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record PatientSession(Long patientId, String medicalRecordNo, boolean isNewPatient) {
    }
}
