package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.config.PatientProperties;
import com.hospital.patient.dto.patient.WechatBindRequest;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * 微信支付前绑定 openid 到当前登录的病人账户（非登录入口）。
 */
@Service
@RequiredArgsConstructor
public class PatientWechatBindService {

    private final PatientRepository patientRepository;
    private final PatientProperties patientProperties;

    public Map<String, Object> bindWechat(WechatBindRequest request) {
        Long patientId = AuthContextHolder.require().getPatientId();
        String openid = resolveOpenid(request.getCode());
        if (patientRepository.findPatientIdByOpenid(openid).filter(id -> !id.equals(patientId)).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该微信已绑定其他患者账户");
        }
        patientRepository.upsertWechatBinding(patientId, openid);
        return Map.of("patientId", patientId, "bound", true);
    }

    private String resolveOpenid(String code) {
        if (patientProperties.getWechat().isMockEnabled()) {
            return "mock_openid_" + sha256(code);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "微信绑定未配置，请开启 mock 模式");
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
}
