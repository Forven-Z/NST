package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.repository.MedicalRecordDiseaseRepository;
import com.hospital.patient.repository.MedicalRecordRepository;
import com.hospital.patient.repository.RegisterRepository;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientRegisterMedicalRecordService {

    private static final Map<Integer, String> RECORD_STATUS_LABELS = Map.of(
            0, "书写中",
            1, "已保存",
            2, "已确诊提交"
    );

    private final RegisterRepository registerRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordDiseaseRepository medicalRecordDiseaseRepository;

    public Map<String, Object> getPatientMedicalRecord(Long registerId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        Map<String, Object> register = registerRepository.findDetailForOwner(registerId, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "无权查看该病历"));
        Map<String, Object> record = medicalRecordRepository.findByRegisterId(registerId, true)
                .map(this::enrichWithDiseases)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "病历不存在或未提交"));
        record.put("visitDate", register.get("workDate"));
        record.put("visitDateLabel", formatVisitDate(register.get("workDate")));
        record.put("noonLabel", register.get("noonLabel"));
        record.put("deptName", register.get("deptName"));
        record.put("doctorName", register.get("doctorName"));
        record.put("registLevelName", register.get("registLevelName"));
        record.put("patientName", register.get("patientName"));
        record.put("medicalRecordNo", register.get("medicalRecordNo"));
        return record;
    }

    private String formatVisitDate(Object visitDate) {
        if (visitDate instanceof LocalDate localDate) {
            return localDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        return visitDate != null ? String.valueOf(visitDate) : "—";
    }

    private Map<String, Object> enrichWithDiseases(Map<String, Object> record) {
        Map<String, Object> enriched = new HashMap<>(record);
        Long medicalRecordId = ((Number) record.get("id")).longValue();
        List<Map<String, Object>> diseaseEntries = medicalRecordDiseaseRepository.findByMedicalRecordId(medicalRecordId);
        enriched.put("diseaseEntries", diseaseEntries);
        enriched.put("diseaseIds", MedicalRecordDiseaseRepository.toDiseaseIds(diseaseEntries));
        int status = ((Number) record.get("status")).intValue();
        enriched.put("statusLabel", RECORD_STATUS_LABELS.getOrDefault(status, String.valueOf(status)));
        return enriched;
    }
}
