package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.dto.patient.PatientProfileResponse;
import com.hospital.patient.dto.patient.PatientProfileUpdateRequest;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatientProfileService {

    private final PatientRepository patientRepository;
    private final PatientIdentityMergeService identityMergeService;

    public PatientProfileResponse getProfile() {
        Long patientId = AuthContextHolder.require().getPatientId();
        return patientRepository.findProfileById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "患者档案不存在"));
    }

    public PatientProfileResponse updateProfile(PatientProfileUpdateRequest request) {
        Long patientId = AuthContextHolder.require().getPatientId();
        PatientIdentityMergeService.MergeResult merge = identityMergeService.mergeOnProfileIdCard(
                patientId,
                request.getIdCard(),
                request.getRealName(),
                request.getGender(),
                request.getBirthDate(),
                request.getPhone(),
                request.getAddress(),
                request.getSettleCategoryId()
        );
        PatientProfileResponse profile = patientRepository.findProfileById(merge.patientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "患者档案不存在"));
        if (merge.merged()) {
            profile.setIdentityMerged(true);
            profile.setAccessToken(merge.accessToken());
            profile.setExpiresIn(merge.expiresIn());
        }
        return profile;
    }
}
