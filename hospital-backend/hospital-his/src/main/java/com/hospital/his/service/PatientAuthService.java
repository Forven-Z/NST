package com.hospital.his.service;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.client.AuthTokenFeignClient;
import com.hospital.his.client.dto.PatientTokenFeignRequest;
import com.hospital.his.client.dto.PatientTokenFeignResponse;
import com.hospital.his.dto.patient.PatientLoginRequest;
import com.hospital.his.dto.patient.WechatLoginResponse;
import com.hospital.his.repository.PatientFamilyRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 患者账户登录与 QQ 式切换（JWT = 当前病人账户，非微信身份）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientAuthService {

    private final PatientRepository patientRepository;
    private final PatientFamilyRepository familyRepository;
    private final PatientLoginPersistence loginPersistence;
    private final AuthTokenFeignClient authTokenFeignClient;

    public WechatLoginResponse login(PatientLoginRequest request) {
        validateLoginProfile(request);
        String phone = normalizePhone(request.getPhone());
        String idCard = normalizeIdCard(request.getIdCard());
        String address = blankToNull(request.getAddress());

        PatientLoginPersistence.UpsertResult upsert = loginPersistence.upsert(request, phone, idCard, address);
        return issueToken(upsert.patientId(), upsert.isNewPatient());
    }

    public WechatLoginResponse switchAccount(Long targetPatientId) {
        Long currentId = AuthContextHolder.require().getPatientId();
        if (currentId.equals(targetPatientId)) {
            return issueToken(targetPatientId, false);
        }
        if (!familyRepository.canSwitchBetween(currentId, targetPatientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权切换到该就诊账户");
        }
        log.info("患者账户切换: {} -> {}", currentId, targetPatientId);
        return issueToken(targetPatientId, false);
    }

    private void validateLoginProfile(PatientLoginRequest request) {
        if (!StringUtils.hasText(request.getRealName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写姓名");
        }
        if (request.getGender() == null || (request.getGender() != 1 && request.getGender() != 2)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择性别");
        }
        if (request.getBirthDate() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择出生日期");
        }
    }

    private WechatLoginResponse issueToken(Long patientId, boolean isNewPatient) {
        String medicalRecordNo = patientRepository.findMedicalRecordNo(patientId);
        PatientTokenFeignRequest tokenRequest = new PatientTokenFeignRequest();
        tokenRequest.setPatientId(patientId);
        tokenRequest.setMedicalRecordNo(medicalRecordNo);

        Result<PatientTokenFeignResponse> tokenResult = authTokenFeignClient.issuePatientToken(tokenRequest);
        if (tokenResult == null || !Boolean.TRUE.equals(tokenResult.getSuccess()) || tokenResult.getData() == null) {
            String message = tokenResult != null ? tokenResult.getMessage() : "auth 无响应";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Token 签发失败: " + message);
        }
        PatientTokenFeignResponse tokenData = tokenResult.getData();
        var profile = patientRepository.findProfileById(patientId).orElse(null);
        return WechatLoginResponse.builder()
                .accessToken(tokenData.getAccessToken())
                .expiresIn(tokenData.getExpiresIn())
                .patientId(patientId)
                .medicalRecordNo(medicalRecordNo)
                .realName(profile != null ? profile.getRealName() : null)
                .isNewPatient(isNewPatient)
                .build();
    }

    private static String normalizePhone(String phone) {
        if (phone == null || !phone.trim().matches("^1\\d{10}$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        return phone.trim();
    }

    private static String normalizeIdCard(String idCard) {
        if (idCard == null || idCard.trim().length() != 18) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写18位身份证号");
        }
        return idCard.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
