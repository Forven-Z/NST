package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrarQueryService {

    private final PatientRepository patientRepository;
    private final BillRepository billRepository;

    public Map<String, Object> listBillsByMedicalRecordNo(String medicalRecordNo, Integer status) {
        requireRegistrar();
        Long patientId = patientRepository.findPatientIdByMedicalRecordNo(medicalRecordNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "病历号不存在"));
        List<Map<String, Object>> list = billRepository.findByPatientId(patientId, status);
        return Map.of(
                "medicalRecordNo", medicalRecordNo,
                "patientId", patientId,
                "list", list
        );
    }

    private void requireRegistrar() {
        var context = AuthContextHolder.require();
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
    }
}
