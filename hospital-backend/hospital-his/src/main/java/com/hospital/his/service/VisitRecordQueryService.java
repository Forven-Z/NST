package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.repository.MedicalRecordRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 患者就诊记录（一次挂号 = 一条就诊）：列表摘要与 Hub 聚合。
 * 供患者小程序 Hub 使用；医生端既往就诊可复用同一契约（Phase 3）。
 */
@Service
@RequiredArgsConstructor
public class VisitRecordQueryService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Map<Integer, String> VISIT_STATE_LABELS = Map.of(
            1, "已挂号",
            2, "接诊中",
            3, "看诊结束");

    private final RegisterRepository registerRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final PatientRepository patientRepository;
    private final PatientFamilyService patientFamilyService;
    private final DoctorMedicalRecordService doctorMedicalRecordService;
    private final RegisterOrdersService registerOrdersService;

    public Map<String, Object> listVisits(Long visitPatientId, int page, int pageSize) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        Long visitId = patientFamilyService.resolveVisitPatientId(visitPatientId);
        if (!patientFamilyService.canAccessVisitPatient(operatorId, visitId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该就诊人记录");
        }

        int offset = Math.max(page - 1, 0) * pageSize;
        List<Map<String, Object>> list = registerRepository
                .findVisitSummariesForOperator(operatorId, visitId, offset, pageSize)
                .stream()
                .map(this::toVisitSummary)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("visitPatientId", visitId);
        return result;
    }

    public Map<String, Object> getVisitHub(Long registerId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        Map<String, Object> reg = registerRepository.findDetailForOwner(registerId, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "就诊记录不存在"));

        int visitState = ((Number) reg.get("visitState")).intValue();
        if (visitState != 1 && visitState != 2 && visitState != 3) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该挂号状态不可作为就诊记录查看");
        }

        Integer mrStatus = medicalRecordRepository.findStatusByRegisterId(registerId).orElse(null);
        boolean hasSubmitted = mrStatus != null && mrStatus == 2;

        Map<String, Object> hub = new HashMap<>();
        hub.put("registerSummary", toRegisterSummary(reg));
        hub.put("medicalRecordStatus", mrStatus);
        hub.put("hasMedicalRecord", hasSubmitted);

        if (hasSubmitted) {
            hub.put("medicalRecord", doctorMedicalRecordService.getPatientMedicalRecord(registerId));
        } else {
            hub.put("medicalRecord", null);
        }

        hub.put("orders", registerOrdersService.getOrdersForPatient(registerId));
        return hub;
    }

    /**
     * 门诊医生查阅指定患者的就诊记录列表（Phase 3）。
     */
    public Map<String, Object> listVisitsForDoctor(Long patientId, int page, int pageSize) {
        assertDoctorStaff();
        if (patientRepository.findMedicalRecordNo(patientId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "患者不存在");
        }

        int offset = Math.max(page - 1, 0) * pageSize;
        List<Map<String, Object>> list = registerRepository
                .findVisitSummariesForPatient(patientId, offset, pageSize)
                .stream()
                .map(this::toVisitSummary)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("patientId", patientId);
        return result;
    }

    /**
     * 门诊医生只读 Hub：病历（含未提交草稿）+ 医嘱。
     */
    public Map<String, Object> getVisitHubForDoctor(Long patientId, Long registerId) {
        assertDoctorStaff();
        Map<String, Object> reg = registerRepository.findDetailByPatient(registerId, patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "就诊记录不存在"));

        int visitState = ((Number) reg.get("visitState")).intValue();
        if (visitState != 1 && visitState != 2 && visitState != 3) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该挂号状态不可作为就诊记录查看");
        }

        Integer mrStatus = medicalRecordRepository.findStatusByRegisterId(registerId).orElse(null);
        boolean submitted = mrStatus != null && mrStatus == 2;
        boolean hasDraft = mrStatus != null && mrStatus >= 1;

        Map<String, Object> hub = new HashMap<>();
        hub.put("registerSummary", toRegisterSummary(reg));
        hub.put("medicalRecordStatus", mrStatus);
        hub.put("hasMedicalRecord", submitted);
        hub.put("hasRecordDraft", hasDraft);

        if (hasDraft) {
            Map<String, Object> record = doctorMedicalRecordService.getMedicalRecordForPatientVisit(registerId, patientId);
            if (!record.isEmpty()) {
                record.put("visitDateLabel", formatVisitDate(reg.get("workDate")));
                record.put("noonLabel", reg.get("noonLabel"));
                record.put("deptName", reg.get("deptName"));
                record.put("doctorName", reg.get("doctorName"));
            }
            hub.put("medicalRecord", record.isEmpty() ? null : record);
        } else {
            hub.put("medicalRecord", null);
        }

        hub.put("orders", registerOrdersService.getOrdersForPatientVisitHistory(registerId, patientId));
        return hub;
    }

    private void assertDoctorStaff() {
        if (AuthContextHolder.require().getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }
    }

    private String formatVisitDate(Object visitDate) {
        if (visitDate instanceof LocalDate localDate) {
            return localDate.format(DISPLAY_DATE);
        }
        return visitDate != null ? String.valueOf(visitDate) : "—";
    }

    private Map<String, Object> toVisitSummary(Map<String, Object> row) {
        Map<String, Object> item = new HashMap<>();
        item.put("registerId", row.get("registerId"));
        item.put("visitState", row.get("visitState"));
        item.put("visitStateLabel", VISIT_STATE_LABELS.getOrDefault(
                ((Number) row.get("visitState")).intValue(), String.valueOf(row.get("visitState"))));
        LocalDate workDate = (LocalDate) row.get("workDate");
        item.put("workDate", workDate);
        item.put("visitDateLabel", workDate != null ? workDate.format(DISPLAY_DATE) : "—");
        item.put("noonLabel", row.get("noonLabel"));
        item.put("deptName", row.get("deptName"));
        item.put("doctorName", row.get("doctorName"));
        item.put("registLevelName", row.get("registLevelName"));
        item.put("patientName", row.get("patientName"));
        item.put("medicalRecordNo", row.get("medicalRecordNo"));

        Integer mrStatus = row.get("medicalRecordStatus") instanceof Number n ? n.intValue() : null;
        item.put("medicalRecordStatus", mrStatus);
        item.put("hasMedicalRecord", mrStatus != null && mrStatus == 2);

        int orderCount = row.get("orderCount") instanceof Number n ? n.intValue() : 0;
        int reportReadyCount = row.get("reportReadyCount") instanceof Number n ? n.intValue() : 0;
        item.put("orderCount", orderCount);
        item.put("reportReadyCount", reportReadyCount);
        item.put("summarySnippet", summarizeSnippet(row.get("diagnosis"), row.get("readme"), orderCount));
        return item;
    }

    private Map<String, Object> toRegisterSummary(Map<String, Object> reg) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("registerId", reg.get("registerId"));
        summary.put("visitState", reg.get("visitState"));
        summary.put("visitStateLabel", VISIT_STATE_LABELS.getOrDefault(
                ((Number) reg.get("visitState")).intValue(), String.valueOf(reg.get("visitState"))));
        LocalDate workDate = (LocalDate) reg.get("workDate");
        summary.put("workDate", workDate);
        summary.put("visitDateLabel", workDate != null ? workDate.format(DISPLAY_DATE) : "—");
        summary.put("noonLabel", reg.get("noonLabel"));
        summary.put("deptName", reg.get("deptName"));
        summary.put("doctorName", reg.get("doctorName"));
        summary.put("registLevelName", reg.get("registLevelName"));
        summary.put("patientName", reg.get("patientName"));
        summary.put("medicalRecordNo", reg.get("medicalRecordNo"));
        return summary;
    }

    private String summarizeSnippet(Object diagnosis, Object readme, int orderCount) {
        String text = diagnosis != null ? String.valueOf(diagnosis).trim() : "";
        if (!StringUtils.hasText(text) && readme != null) {
            text = String.valueOf(readme).trim();
        }
        if (StringUtils.hasText(text)) {
            return text.length() <= 80 ? text : text.substring(0, 80) + "…";
        }
        if (orderCount > 0) {
            return "医嘱 " + orderCount + " 项";
        }
        return "就诊进行中，暂无文书摘要";
    }
}
