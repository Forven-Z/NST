package com.hospital.his.service;

import com.hospital.his.dto.patient.WechatLoginRequest;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.util.BizNoGenerator;
import com.hospital.his.util.IdCardUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PatientWechatService {

    private final PatientRepository patientRepository;
    private final PatientIdentityMergeService identityMergeService;

    @Transactional
    public WechatAuthService.PatientSession upsertSession(String openid, WechatLoginRequest request) {
        Long patientId = patientRepository.findPatientIdByOpenid(openid).orElse(null);
        boolean isNewPatient = false;

        if (patientId == null) {
            Optional<Long> mergedId = identityMergeService.resolvePatientIdForNewLogin(openid, request.getIdCard());
            if (mergedId.isPresent()) {
                patientId = mergedId.get();
                applyInitialProfile(patientId, request);
            } else {
                isNewPatient = true;
                String medicalRecordNo = BizNoGenerator.medicalRecordNo();
                String realName = resolveRealName(request);
                patientId = patientRepository.insertPatient(medicalRecordNo, realName);
                patientRepository.upsertWechatBinding(patientId, openid);
                applyInitialProfile(patientId, request);
            }
        } else {
            patientRepository.upsertWechatBinding(patientId, openid);
        }

        String medicalRecordNo = patientRepository.findMedicalRecordNo(patientId);
        return new WechatAuthService.PatientSession(patientId, medicalRecordNo, isNewPatient);
    }

    private void applyInitialProfile(Long patientId, WechatLoginRequest request) {
        if (!hasProfilePayload(request)) {
            return;
        }
        String phone = blankToNull(request.getPhone());
        if (phone != null) {
            patientRepository.assertPhoneAvailable(phone, patientId);
        }
        String idCard = blankToNull(request.getIdCard());
        patientRepository.updateProfile(
                patientId,
                resolveRealName(request),
                request.getGender(),
                request.getBirthDate(),
                IdCardUtils.resolveAge(null, request.getBirthDate()),
                phone,
                idCard,
                blankToNull(request.getAddress()),
                null
        );
    }

    private static boolean hasProfilePayload(WechatLoginRequest request) {
        return StringUtils.hasText(request.getRealName())
                || StringUtils.hasText(request.getNickName())
                || StringUtils.hasText(request.getIdCard())
                || request.getGender() != null
                || request.getBirthDate() != null
                || StringUtils.hasText(request.getPhone())
                || StringUtils.hasText(request.getAddress());
    }

    private static String resolveRealName(WechatLoginRequest request) {
        if (StringUtils.hasText(request.getRealName())) {
            return request.getRealName().trim();
        }
        if (StringUtils.hasText(request.getNickName())) {
            return request.getNickName().trim();
        }
        return "微信用户";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
