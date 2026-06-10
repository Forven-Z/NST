package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.CheckRequestRepository;
import com.hospital.his.repository.DisposalRequestRepository;
import com.hospital.his.repository.InspectionRequestRepository;
import com.hospital.his.repository.PrescriptionRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
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

    private static final Map<Integer, String> MEDICAL_ORDER_STATUS_LABELS = Map.of(
            10, "已开立",
            20, "已缴费",
            30, "执行中",
            40, "已出结果",
            50, "已退费");

    private static final Map<Integer, String> PRESCRIPTION_STATUS_LABELS = Map.of(
            10, "已开立",
            20, "已缴费",
            30, "已发药",
            40, "已退药",
            50, "已退费");

    private final RegisterRepository registerRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final PrescriptionRepository prescriptionRepository;

    public Map<String, Object> getOrdersForDoctor(Long registerId) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        if (doctorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }

        Map<String, Object> register = registerRepository.findById(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        if (!doctorId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看本队列患者的医嘱");
        }

        return buildOrders(registerId);
    }

    public Map<String, Object> buildOrders(Long registerId) {
        List<Map<String, Object>> inspections = inspectionRequestRepository.findByRegisterId(registerId);
        List<Map<String, Object>> checks = checkRequestRepository.findByRegisterId(registerId);
        List<Map<String, Object>> disposals = disposalRequestRepository.findByRegisterId(registerId);
        List<Map<String, Object>> prescriptions = prescriptionRepository.findByRegisterId(registerId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : inspections) {
            list.add(toListItem(
                    "inspection",
                    "检验",
                    ((Number) row.get("inspectionRequestId")).longValue(),
                    (String) row.get("itemName"),
                    ((Number) row.get("status")).intValue(),
                    MEDICAL_ORDER_STATUS_LABELS));
        }
        for (Map<String, Object> row : checks) {
            list.add(toListItem(
                    "check",
                    "检查",
                    ((Number) row.get("checkRequestId")).longValue(),
                    (String) row.get("itemName"),
                    ((Number) row.get("status")).intValue(),
                    MEDICAL_ORDER_STATUS_LABELS));
        }
        for (Map<String, Object> row : disposals) {
            list.add(toListItem(
                    "disposal",
                    "处置记录",
                    ((Number) row.get("disposalRequestId")).longValue(),
                    (String) row.get("itemName"),
                    ((Number) row.get("status")).intValue(),
                    MEDICAL_ORDER_STATUS_LABELS));
        }
        for (Map<String, Object> row : prescriptions) {
            list.add(toListItem(
                    "prescription",
                    "处方",
                    ((Number) row.get("prescriptionId")).longValue(),
                    prescriptionItemName(row),
                    ((Number) row.get("status")).intValue(),
                    PRESCRIPTION_STATUS_LABELS));
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

    private Map<String, Object> toListItem(String kind, String typeLabel, long requestId, String itemName,
                                           int status, Map<Integer, String> statusLabels) {
        Map<String, Object> item = new HashMap<>();
        item.put("kind", kind);
        item.put("typeLabel", typeLabel);
        item.put("requestId", requestId);
        item.put("itemName", itemName);
        item.put("status", status);
        item.put("statusLabel", statusLabels.getOrDefault(status, String.valueOf(status)));
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
