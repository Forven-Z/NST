package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.doctor.MedicalRecordSaveRequest;
import com.hospital.his.repository.MedicalRecordRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DoctorMedicalRecordService {

    private final RegisterRepository registerRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientFamilyService patientFamilyService;

    public Map<String, Object> getMedicalRecord(Long registerId) {
        assertDoctorOwnsRegister(registerId);
        return medicalRecordRepository.findByRegisterId(registerId)
                .map(m -> new HashMap<String, Object>(m))
                .orElseGet(HashMap::new);
    }

    @Transactional
    public Map<String, Object> saveMedicalRecord(Long registerId, MedicalRecordSaveRequest request) {
        Map<String, Object> register = assertDoctorOwnsRegister(registerId);
        Long patientId = ((Number) register.get("patientId")).longValue();
        Long doctorId = AuthContextHolder.require().getEmployeeId();

        if (medicalRecordRepository.findByRegisterId(registerId).isEmpty()) {
            medicalRecordRepository.insert(registerId, patientId, doctorId);
        }

        medicalRecordRepository.update(
                registerId,
                request.getReadme(),
                request.getPresent(),
                request.getPresentTreat(),
                request.getHistory(),
                request.getAllergy(),
                request.getPhysique(),
                request.getDiagnosis(),
                request.getCure(),
                request.getCheckAdvice(),
                request.getInspectionAdvice()
        );

        return medicalRecordRepository.findByRegisterId(registerId).orElse(Map.of());
    }

    public Map<String, Object> getPatientMedicalRecord(Long registerId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        registerRepository.findDetailForOwner(registerId, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "无权查看该病历"));
        return medicalRecordRepository.findByRegisterId(registerId, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "病历不存在或未提交"));
    }

    private Map<String, Object> assertDoctorOwnsRegister(Long registerId) {
        Long employeeId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> register = registerRepository.findById(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        if (!employeeId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该挂号");
        }
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.IN_CONSULTATION && visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可编辑病历");
        }
        return register;
    }
}
