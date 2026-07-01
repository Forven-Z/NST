package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.repository.CheckRequestRepository;
import com.hospital.patient.repository.DisposalRequestRepository;
import com.hospital.patient.repository.InspectionRequestRepository;
import com.hospital.patient.repository.PrescriptionRepository;
import com.hospital.patient.repository.RegisterRepository;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegisterOrdersService {

    private static final Map<Integer, String> PATIENT_PRESCRIPTION_STATUS_LABELS = Map.of(
            10, "待缴费",
            15, "药师驳回",
            20, "待取药",
            30, "已发药",
            40, "已退药",
            50, "已退费");

    private final RegisterRepository registerRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientFamilyService patientFamilyService;

    public Map<String, Object> getOrdersForPatient(Long registerId) {
        var context = AuthContextHolder.require();
        if (!context.isPatient()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要患者身份");
        }
        Map<String, Object> register = registerRepository.findById(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        Long patientId = ((Number) register.get("patientId")).longValue();
        if (!patientFamilyService.canAccessVisitPatient(context.getPatientId(), patientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该就诊医嘱");
        }
        Map<String, Object> result = buildOrders(registerId);
        registerRepository.findDetailForOwner(registerId, context.getPatientId())
                .ifPresent(reg -> result.put("registerSummary", buildRegisterSummary(reg)));
        return result;
    }

    private Map<String, Object> buildRegisterSummary(Map<String, Object> reg) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("registerId", reg.get("registerId"));
        summary.put("deptName", displayText(reg.get("deptName")));
        summary.put("doctorName", displayText(reg.get("doctorName")));
        summary.put("workDate", reg.get("workDate"));
        summary.put("noonLabel", displayText(reg.get("noonLabel")));
        summary.put("patientName", displayText(reg.get("patientName")));
        summary.put("registLevelName", displayText(reg.get("registLevelName")));
        return summary;
    }

    private static String displayText(Object value) {
        if (value == null) {
            return "—";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "—" : text;
    }

    private Map<String, Object> buildOrders(Long registerId) {
        List<Map<String, Object>> inspections = inspectionRequestRepository.findByRegisterId(registerId);
        List<Map<String, Object>> checks = checkRequestRepository.findByRegisterId(registerId);
        List<Map<String, Object>> disposals = disposalRequestRepository.findByRegisterId(registerId);
        List<Map<String, Object>> prescriptions = prescriptionRepository.findByRegisterId(registerId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : inspections) {
            list.add(toMedicalListItem(
                    "inspection",
                    "检验",
                    ((Number) row.get("inspectionRequestId")).longValue(),
                    (String) row.get("itemName"),
                    ((Number) row.get("status")).intValue()));
        }
        for (Map<String, Object> row : checks) {
            list.add(toMedicalListItem(
                    "check",
                    "检查",
                    ((Number) row.get("checkRequestId")).longValue(),
                    (String) row.get("itemName"),
                    ((Number) row.get("status")).intValue()));
        }
        for (Map<String, Object> row : disposals) {
            list.add(toMedicalListItem(
                    "disposal",
                    "处置记录",
                    ((Number) row.get("disposalRequestId")).longValue(),
                    (String) row.get("itemName"),
                    ((Number) row.get("status")).intValue()));
        }
        for (Map<String, Object> row : prescriptions) {
            list.add(toPrescriptionListItem(row));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);
        result.put("list", list);
        result.put("checks", checks);
        result.put("inspections", inspections);
        result.put("disposals", disposals);
        result.put("prescriptions", prescriptions);
        return result;
    }

    private Map<String, Object> toMedicalListItem(String kind, String typeLabel, long requestId,
                                                  String itemName, int status) {
        Map<String, Object> item = new HashMap<>();
        item.put("kind", kind);
        item.put("typeLabel", typeLabel);
        item.put("requestId", requestId);
        item.put("itemName", StringUtils.hasText(itemName) ? itemName : "—");
        item.put("status", status);
        item.put("statusLabel", patientMedicalStatusLabel(kind, status));
        item.put("action", patientOrderAction(kind, status));
        return item;
    }

    private static String patientMedicalStatusLabel(String kind, int status) {
        if (status == 20) {
            return switch (kind) {
                case "inspection" -> "待检验";
                case "check" -> "待检查";
                default -> "待执行";
            };
        }
        return switch (status) {
            case 10 -> "待缴费";
            case 30 -> "执行中";
            case 40 -> "已出报告";
            case 50 -> "已退费";
            default -> String.valueOf(status);
        };
    }

    private static String patientOrderAction(String kind, int status) {
        if (status == 10) {
            return "pay";
        }
        if ("prescription".equals(kind)) {
            if (status == 15 || status == 20 || status == 30 || status == 40) {
                return "prescription";
            }
            return "none";
        }
        if (status >= 40) {
            return "report";
        }
        return "none";
    }

    private Map<String, Object> toPrescriptionListItem(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        Map<String, Object> item = toMedicalListItem(
                "prescription",
                "处方",
                ((Number) row.get("prescriptionId")).longValue(),
                prescriptionItemName(row),
                status);
        item.put("statusLabel", PATIENT_PRESCRIPTION_STATUS_LABELS.getOrDefault(status, String.valueOf(status)));
        item.put("action", patientOrderAction("prescription", status));
        return item;
    }

    @SuppressWarnings("unchecked")
    private String prescriptionItemName(Map<String, Object> prescription) {
        Object itemsObj = prescription.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            return "处方";
        }
        String joined = items.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(item -> item.get("drugName"))
                .filter(name -> name instanceof String s && StringUtils.hasText(s))
                .map(String.class::cast)
                .collect(Collectors.joining("、"));
        return StringUtils.hasText(joined) ? joined : "处方";
    }
}
