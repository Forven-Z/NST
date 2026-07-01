package com.hospital.patient.service;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.client.AuthTokenFeignClient;
import com.hospital.patient.client.dto.PatientTokenFeignRequest;
import com.hospital.patient.client.dto.PatientTokenFeignResponse;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.util.IdCardUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 微信登录占位 patient 与家属预建 patient 按身份证合并（ADR-016）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientIdentityMergeService {

    private final PatientRepository patientRepository;
    private final AuthTokenFeignClient authTokenFeignClient;

    /**
     * 登录前：若提供身份证且已有无微信绑定的 patient，直接绑定并返回该 ID。
     */
    public Optional<Long> resolvePatientIdForNewLogin(String openid, String idCard) {
        if (!StringUtils.hasText(idCard)) {
            return Optional.empty();
        }
        Long targetId = patientRepository.findPatientIdByIdCard(idCard.trim())
                .orElse(null);
        if (targetId == null) {
            return Optional.empty();
        }
        if (patientRepository.hasWechatBinding(targetId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该身份证已绑定其他微信账号");
        }
        patientRepository.upsertWechatBinding(targetId, openid);
        log.info("微信登录身份证合并: openid 绑定至已有 patientId={}", targetId);
        return Optional.of(targetId);
    }

    /**
     * 完善档案：占位 patient 与已有 patient 合并；返回合并后 patientId 与新 Token（若发生合并）。
     */
    @Transactional
    public MergeResult mergeOnProfileIdCard(Long currentPatientId, String idCard,
                                            String realName, Integer gender, java.time.LocalDate birthDate,
                                            String phone, String address, Long settleCategoryId) {
        if (StringUtils.hasText(phone)) {
            patientRepository.assertPhoneAvailable(phone, currentPatientId);
        }
        if (!StringUtils.hasText(idCard)) {
            patientRepository.updateProfile(currentPatientId, realName, gender, birthDate,
                    IdCardUtils.resolveAge(null, birthDate), phone, null, address, settleCategoryId);
            return MergeResult.unchanged(currentPatientId);
        }

        String normalizedIdCard = idCard.trim();
        Optional<Long> existingOpt = patientRepository.findPatientIdByIdCard(normalizedIdCard);
        if (existingOpt.isEmpty() || existingOpt.get().equals(currentPatientId)) {
            patientRepository.updateProfile(currentPatientId, realName, gender, birthDate,
                    IdCardUtils.resolveAge(null, birthDate), phone, normalizedIdCard, address, settleCategoryId);
            return MergeResult.unchanged(currentPatientId);
        }

        Long targetPatientId = existingOpt.get();
        if (patientRepository.hasWechatBinding(targetPatientId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该身份证已绑定其他微信账号");
        }
        if (StringUtils.hasText(phone)) {
            patientRepository.assertPhoneAvailable(phone, targetPatientId);
        }

        String openid = patientRepository.findOpenidByPatientId(currentPatientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "未找到微信绑定，无法合并档案"));

        patientRepository.updateProfile(targetPatientId, realName, gender, birthDate,
                IdCardUtils.resolveAge(null, birthDate), phone, normalizedIdCard, address, settleCategoryId);
        patientRepository.rebindWechatPatient(openid, targetPatientId);
        patientRepository.softDeletePatient(currentPatientId);

        log.info("档案身份证合并: placeholder patientId={} 合并至 patientId={}", currentPatientId, targetPatientId);

        String medicalRecordNo = patientRepository.findMedicalRecordNo(targetPatientId);
        String accessToken = issuePatientToken(targetPatientId, medicalRecordNo);
        return MergeResult.merged(targetPatientId, accessToken, medicalRecordNo);
    }

    private String issuePatientToken(Long patientId, String medicalRecordNo) {
        PatientTokenFeignRequest tokenRequest = new PatientTokenFeignRequest();
        tokenRequest.setPatientId(patientId);
        tokenRequest.setMedicalRecordNo(medicalRecordNo);
        Result<PatientTokenFeignResponse> tokenResult = authTokenFeignClient.issuePatientToken(tokenRequest);
        if (tokenResult == null || !Boolean.TRUE.equals(tokenResult.getSuccess()) || tokenResult.getData() == null) {
            String message = tokenResult != null ? tokenResult.getMessage() : "auth 无响应";
            throw new BusinessException(ErrorCode.BAD_REQUEST, "合并后 Token 签发失败: " + message);
        }
        return tokenResult.getData().getAccessToken();
    }

    public record MergeResult(Long patientId, boolean merged, String accessToken, Integer expiresIn,
                              String medicalRecordNo) {
        static MergeResult unchanged(Long patientId) {
            return new MergeResult(patientId, false, null, null, null);
        }

        static MergeResult merged(Long patientId, String accessToken, String medicalRecordNo) {
            return new MergeResult(patientId, true, accessToken, 7200, medicalRecordNo);
        }
    }
}
