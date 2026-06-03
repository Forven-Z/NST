package com.hospital.his.service;

import com.hospital.his.dto.patient.WechatLoginRequest;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.util.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PatientWechatService {

    private final PatientRepository patientRepository;

    @Transactional
    public WechatAuthService.PatientSession upsertSession(String openid, WechatLoginRequest request) {
        boolean isNewPatient = false;
        Long patientId = patientRepository.findPatientIdByOpenid(openid).orElse(null);

        if (patientId == null) {
            isNewPatient = true;
            String medicalRecordNo = BizNoGenerator.medicalRecordNo();
            String realName = StringUtils.hasText(request.getNickName()) ? request.getNickName() : "微信用户";
            patientId = patientRepository.insertPatient(medicalRecordNo, realName);
            patientRepository.upsertWechatBinding(patientId, openid);
        } else {
            patientRepository.upsertWechatBinding(patientId, openid);
        }

        String medicalRecordNo = patientRepository.findMedicalRecordNo(patientId);
        return new WechatAuthService.PatientSession(patientId, medicalRecordNo, isNewPatient);
    }
}
