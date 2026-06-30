package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.pharmacy.RejectPrescriptionRequest;
import com.hospital.his.order.state.OrderStatusCoordinator;
import com.hospital.his.repository.PrescriptionRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private static final Map<Integer, String> STATUS_LABELS = Map.of(
            PrescriptionStatus.ORDERED, "已开立",
            PrescriptionStatus.PHARMACY_REJECTED, "药师驳回",
            PrescriptionStatus.PAID, "已缴费",
            PrescriptionStatus.DISPENSED, "已发药",
            PrescriptionStatus.RETURNED, "已退药",
            PrescriptionStatus.REFUNDED, "已退费");

    private final PrescriptionRepository prescriptionRepository;
    private final RefundService refundService;
    private final OrderStatusCoordinator orderStatusCoordinator;

    public Map<String, Object> listPending(Integer status, int page, int pageSize) {
        requirePharmacist();
        int offset = Math.max(page - 1, 0) * pageSize;
        Integer queryStatus = status != null ? status : PrescriptionStatus.PAID;
        return Map.of(
                "list", prescriptionRepository.findPending(queryStatus, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    public Map<String, Object> getPrescriptionDetail(Long prescriptionId) {
        requirePharmacist();
        Map<String, Object> detail = prescriptionRepository.findDetailById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));
        int status = ((Number) detail.get("status")).intValue();
        detail.put("statusLabel", STATUS_LABELS.getOrDefault(status, String.valueOf(status)));
        detail.put("items", prescriptionRepository.findItemsWithStockByPrescriptionId(prescriptionId));
        return detail;
    }

    @Transactional
    public Map<String, Object> rejectDispense(Long prescriptionId, RejectPrescriptionRequest request) {
        Long pharmacistId = requirePharmacist();
        String reason = request.getReason() != null ? request.getReason().trim() : "";
        if (reason.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写拒绝原因");
        }

        prescriptionRepository.findByIdForUpdate(prescriptionId)
                .filter(p -> ((Number) p.get("status")).intValue() == PrescriptionStatus.PAID)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费未发药处方可拒绝"));

        refundService.refundPrescriptionBillForPharmacyReject(prescriptionId, reason, pharmacistId);

        orderStatusCoordinator.pharmacyReject(prescriptionId, pharmacistId, reason);

        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionId", prescriptionId);
        result.put("status", PrescriptionStatus.PHARMACY_REJECTED);
        result.put("message", "已拒绝发药并退费，处方已退回开方医生");
        return result;
    }

    @Transactional
    public Map<String, Object> dispense(Long prescriptionId) {
        Long pharmacistId = requirePharmacist();
        prescriptionRepository.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));

        orderStatusCoordinator.dispensePrescription(prescriptionId, pharmacistId);

        List<Map<String, Object>> items = prescriptionRepository.findItemsByPrescriptionId(prescriptionId);
        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionId", prescriptionId);
        result.put("status", PrescriptionStatus.DISPENSED);
        result.put("items", items);
        return result;
    }

    @Transactional
    public Map<String, Object> returnDrug(Long prescriptionId) {
        requirePharmacist();
        prescriptionRepository.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));

        orderStatusCoordinator.returnPrescriptionDrug(prescriptionId);

        List<Map<String, Object>> items = prescriptionRepository.findItemsByPrescriptionId(prescriptionId);
        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionId", prescriptionId);
        result.put("status", PrescriptionStatus.RETURNED);
        result.put("items", items);
        result.put("message", "退药成功，请至收费窗口办理退费");
        return result;
    }

    private Long requirePharmacist() {
        var context = AuthContextHolder.require();
        if (context.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要药师身份");
        }
        List<String> roles = context.getRoles();
        if (roles != null && (roles.contains("PHARMACIST") || roles.contains("ADMIN"))) {
            return context.getEmployeeId();
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "需要药师角色 PHARMACIST");
    }
}
