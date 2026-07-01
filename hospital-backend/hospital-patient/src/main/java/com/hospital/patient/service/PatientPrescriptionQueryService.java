package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.repository.PrescriptionRepository;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientPrescriptionQueryService {

    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            10, "待缴费",
            15, "药师驳回",
            20, "待取药",
            30, "已发药",
            40, "已退药",
            50, "已退费");

    private final PrescriptionRepository prescriptionRepository;
    private final PatientFamilyService patientFamilyService;

    public Map<String, Object> getDetail(Long prescriptionId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        Map<String, Object> detail = prescriptionRepository.findDetailById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));
        Long patientId = ((Number) detail.get("patientId")).longValue();
        if (!patientFamilyService.canAccessVisitPatient(operatorId, patientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该处方");
        }
        detail.put("items", prescriptionRepository.findItemsByPrescriptionId(prescriptionId));
        int status = ((Number) detail.get("status")).intValue();
        detail.put("statusLabel", STATUS_LABELS.getOrDefault(status, String.valueOf(status)));
        detail.put("pickupHint", pickupHint(status));
        return detail;
    }

    private String pickupHint(int status) {
        return switch (status) {
            case 10 -> "请先完成处方缴费";
            case 15 -> "处方已被药师驳回并退费，请联系医生修改后重新开方";
            case 20 -> "请携带就诊卡至门诊药房窗口取药";
            case 30 -> "药品已发放，请按医嘱用药";
            case 40 -> "处方已退药";
            case 50 -> "处方费用已退回";
            default -> "如有疑问请联系窗口";
        };
    }
}
