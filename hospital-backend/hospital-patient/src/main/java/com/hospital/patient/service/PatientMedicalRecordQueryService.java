package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.repository.MedicalRecordRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientMedicalRecordQueryService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientFamilyService patientFamilyService;
    private final PatientRepository patientRepository;

    public Map<String, Object> listRecords(Long visitPatientId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        Long visitId = patientFamilyService.resolveVisitPatientId(visitPatientId);
        if (!patientFamilyService.canAccessVisitPatient(operatorId, visitId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该就诊人病历");
        }

        List<Map<String, Object>> list = medicalRecordRepository.findSubmittedSummariesByPatientId(visitId).stream()
                .map(this::toListItem)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("visitPatientId", visitId);
        return result;
    }

    private Map<String, Object> toListItem(Map<String, Object> row) {
        Map<String, Object> item = new HashMap<>(row);
        LocalDate visitDate = (LocalDate) row.get("visitDate");
        OffsetDateTime recordTime = (OffsetDateTime) row.get("recordTime");
        item.put("visitDateLabel", visitDate != null ? visitDate.format(DISPLAY_DATE) : "—");
        item.put("recordTimeLabel", recordTime != null ? recordTime.format(DISPLAY_TIME) : "—");
        item.put("summary", summarizeDiagnosis(row.get("diagnosis"), row.get("readme")));
        return item;
    }

    private String summarizeDiagnosis(Object diagnosis, Object readme) {
        String text = diagnosis != null ? String.valueOf(diagnosis).trim() : "";
        if (!StringUtils.hasText(text) && readme != null) {
            text = String.valueOf(readme).trim();
        }
        if (!StringUtils.hasText(text)) {
            return "已提交病历，点击查看详情";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }
}
