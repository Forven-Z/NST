package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.patient.PatientProfileResponse;
import com.hospital.his.dto.patient.PatientProfileUpdateRequest;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatientProfileService {

    private final PatientRepository patientRepository;

    public PatientProfileResponse getProfile() {
        Long patientId = AuthContextHolder.require().getPatientId();
        return patientRepository.findProfileById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "患者档案不存在"));
    }

    public PatientProfileResponse updateProfile(PatientProfileUpdateRequest request) {
        Long patientId = AuthContextHolder.require().getPatientId();
        patientRepository.updateProfile(
                patientId,
                request.getRealName(),
                request.getGender(),
                request.getBirthDate(),
                request.getPhone(),
                request.getIdCard(),
                request.getAddress(),
                request.getSettleCategoryId()
        );
        return getProfile();
    }
}
