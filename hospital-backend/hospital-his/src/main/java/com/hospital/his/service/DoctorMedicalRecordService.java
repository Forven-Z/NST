package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.doctor.MedicalRecordSaveRequest;
import com.hospital.his.repository.MedicalRecordDiseaseRepository;
import com.hospital.his.repository.MedicalRecordRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DoctorMedicalRecordService {

    private final RegisterRepository registerRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordDiseaseRepository medicalRecordDiseaseRepository;
    private final PatientFamilyService patientFamilyService;

    private static final Map<Integer, String> RECORD_STATUS_LABELS = Map.of(
            0, "书写中",
            1, "已保存",
            2, "已确诊提交"
    );

    public Map<String, Object> getMedicalRecord(Long registerId) {
        assertDoctorOwnsRegister(registerId);
        return medicalRecordRepository.findByRegisterId(registerId)
                .map(this::enrichWithDiseases)
                .orElseGet(HashMap::new);
    }

    @Transactional
    public Map<String, Object> saveMedicalRecord(Long registerId, MedicalRecordSaveRequest request) {
        Map<String, Object> register = assertDoctorOwnsRegister(registerId);
        int currentStatus = medicalRecordRepository.findStatusByRegisterId(registerId).orElse(0);
        persistMedicalRecord(registerId, register, request);
        if (currentStatus < 2) {
            medicalRecordRepository.updateStatus(registerId, 1);
        }
        return medicalRecordRepository.findByRegisterId(registerId)
                .map(this::enrichWithDiseases)
                .orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> submitMedicalRecord(Long registerId, MedicalRecordSaveRequest request) {
        Map<String, Object> register = assertDoctorOwnsRegister(registerId);
        persistMedicalRecord(registerId, register, request);
        medicalRecordRepository.updateStatus(registerId, 2);
        return medicalRecordRepository.findByRegisterId(registerId)
                .map(this::enrichWithDiseases)
                .orElse(Map.of());
    }

    private long persistMedicalRecord(Long registerId, Map<String, Object> register, MedicalRecordSaveRequest request) {
        Long patientId = ((Number) register.get("patientId")).longValue();
        Long doctorId = AuthContextHolder.require().getEmployeeId();

        long medicalRecordId;
        var existing = medicalRecordRepository.findByRegisterId(registerId);
        if (existing.isEmpty()) {
            medicalRecordId = medicalRecordRepository.insert(registerId, patientId, doctorId);
        } else {
            medicalRecordId = ((Number) existing.get().get("id")).longValue();
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

        if (hasDiseasePayload(request)) {
            medicalRecordDiseaseRepository.replaceAll(medicalRecordId, resolveDiseaseEntries(request));
        }
        return medicalRecordId;
    }

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

    /** 医生只读查阅患者某次就诊病历（含已保存未提交） */
    public Map<String, Object> getMedicalRecordForPatientVisit(Long registerId, Long patientId) {
        if (AuthContextHolder.require().getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }
        registerRepository.findDetailByPatient(registerId, patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "就诊记录不存在"));
        return medicalRecordRepository.findByRegisterId(registerId)
                .map(this::enrichWithDiseases)
                .orElse(Map.of());
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

    private boolean hasDiseasePayload(MedicalRecordSaveRequest request) {
        return request.getDiseaseEntries() != null || request.getDiseaseIds() != null;
    }

    private List<Map<String, Object>> resolveDiseaseEntries(MedicalRecordSaveRequest request) {
        if (request.getDiseaseEntries() != null && !request.getDiseaseEntries().isEmpty()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (MedicalRecordSaveRequest.DiseaseEntry entry : request.getDiseaseEntries()) {
                if (entry.getDiseaseId() == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("diseaseId", entry.getDiseaseId());
                row.put("diseaseType", entry.getDiseaseType() != null ? entry.getDiseaseType() : 2);
                entries.add(row);
            }
            return entries;
        }

        List<Long> diseaseIds = request.getDiseaseIds();
        if (diseaseIds == null || diseaseIds.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("diseaseId", diseaseIds.get(0));
        primary.put("diseaseType", 1);
        entries.add(primary);

        for (int i = 1; i < diseaseIds.size(); i++) {
            Long diseaseId = diseaseIds.get(i);
            if (diseaseId == null || diseaseId.equals(diseaseIds.get(0))) {
                continue;
            }
            Map<String, Object> secondary = new LinkedHashMap<>();
            secondary.put("diseaseId", diseaseId);
            secondary.put("diseaseType", 2);
            entries.add(secondary);
        }
        return entries;
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
