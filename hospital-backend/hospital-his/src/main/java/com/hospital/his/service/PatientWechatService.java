package com.hospital.his.service;

import com.hospital.his.dto.patient.WechatLoginRequest;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.util.BizNoGenerator;
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
            } else {
                isNewPatient = true;
                String medicalRecordNo = BizNoGenerator.medicalRecordNo();
                String realName = StringUtils.hasText(request.getNickName()) ? request.getNickName() : "微信用户";
                patientId = patientRepository.insertPatient(medicalRecordNo, realName);
                patientRepository.upsertWechatBinding(patientId, openid);
            }
        } else {
            patientRepository.upsertWechatBinding(patientId, openid);
        }

        String medicalRecordNo = patientRepository.findMedicalRecordNo(patientId);
        return new WechatAuthService.PatientSession(patientId, medicalRecordNo, isNewPatient);
    }
}
