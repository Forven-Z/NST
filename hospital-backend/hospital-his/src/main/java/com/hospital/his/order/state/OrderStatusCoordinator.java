package com.hospital.his.order.state;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.order.MedTechOrderEvent;
import com.hospital.common.order.MedTechOrderTransitionException;
import com.hospital.common.order.MedTechOrderTransitions;
import com.hospital.common.order.PrescriptionEvent;
import com.hospital.common.order.PrescriptionTransitionException;
import com.hospital.common.order.PrescriptionTransitions;
import com.hospital.his.order.MedTechOrderKind;
import com.hospital.his.order.PrescriptionInventorySupport;
import com.hospital.his.repository.CheckRequestRepository;
import com.hospital.his.repository.DisposalRequestRepository;
import com.hospital.his.repository.InspectionRequestRepository;
import com.hospital.his.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 医嘱 {@code status} 写库唯一入口（步骤 ②）：医技 SM1 + 处方 SM2。
 */
@Service
@RequiredArgsConstructor
public class OrderStatusCoordinator {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionInventorySupport prescriptionInventorySupport;

    @Transactional
    public void payMedTechOrder(MedTechOrderKind kind, Long orderId) {
        applyMedTechTransition(kind, orderId, MedTechOrderEvent.PAY, "仅已开立医嘱可缴费");
    }

    @Transactional
    public void refundMedTechOrder(MedTechOrderKind kind, Long orderId) {
        applyMedTechTransition(kind, orderId, MedTechOrderEvent.REFUND, "检验已执行或已退费，不可退款");
    }

    @Transactional
    public void payPrescription(Long prescriptionId) {
        applyPrescriptionTransition(prescriptionId, PrescriptionEvent.PAY, "仅已开立处方可缴费");
    }

    @Transactional
    public void refundPrescription(Long prescriptionId) {
        int from = currentPrescriptionStatus(prescriptionId);
        if (PrescriptionTransitions.restoreStockOnRefund(from)) {
            prescriptionInventorySupport.restorePrescription(prescriptionId);
        }
        applyPrescriptionTransition(prescriptionId, PrescriptionEvent.REFUND, "处方状态不允许退费");
    }

    @Transactional
    public void resubmitPrescription(Long prescriptionId, BigDecimal totalAmount) {
        int from = currentPrescriptionStatus(prescriptionId);
        PrescriptionTransitions.assertTransition(from, PrescriptionEvent.RESUBMIT);
        prescriptionInventorySupport.validateAndDeductPrescription(prescriptionId);
        if (prescriptionRepository.clearRejectFieldsAndSetOrdered(prescriptionId, totalAmount) == 0) {
            throw mismatchAfterStock(PrescriptionEvent.RESUBMIT, "仅药师驳回处方可重新提交");
        }
    }

    @Transactional
    public void pharmacyReject(Long prescriptionId, Long pharmacistId, String reason) {
        int from = currentPrescriptionStatus(prescriptionId);
        PrescriptionTransitions.assertTransition(from, PrescriptionEvent.PHARMACY_REJECT);
        prescriptionInventorySupport.restorePrescription(prescriptionId);
        if (prescriptionRepository.markPharmacyRejected(prescriptionId, pharmacistId, reason) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费未发药处方可拒绝");
        }
    }

    @Transactional
    public void dispensePrescription(Long prescriptionId, Long pharmacistId) {
        int from = currentPrescriptionStatus(prescriptionId);
        PrescriptionTransitions.assertTransition(from, PrescriptionEvent.DISPENSE);
        if (prescriptionRepository.markDispensedIfCurrent(prescriptionId, from, pharmacistId) == 0) {
            assertPrescriptionMismatch(from, PrescriptionEvent.DISPENSE, "仅已缴费处方可发药");
        }
    }

    @Transactional
    public void returnPrescriptionDrug(Long prescriptionId) {
        int from = currentPrescriptionStatus(prescriptionId);
        PrescriptionTransitions.assertTransition(from, PrescriptionEvent.RETURN_DRUG);
        prescriptionInventorySupport.restorePrescription(prescriptionId);
        if (prescriptionRepository.markReturnedIfCurrent(prescriptionId, from) == 0) {
            assertPrescriptionMismatch(from, PrescriptionEvent.RETURN_DRUG, "仅已发药处方可退药");
        }
    }

    /** 开立后预扣库存（ORDER 已写入 status=10）。 */
    @Transactional
    public void onPrescriptionOrdered(List<Map<String, Object>> itemSnapshots) {
        prescriptionInventorySupport.validateAndDeduct(itemSnapshots);
    }

    private void applyMedTechTransition(MedTechOrderKind kind, Long orderId, MedTechOrderEvent event,
                                        String mismatchHint) {
        int from = currentMedTechStatus(kind, orderId);
        int to = MedTechOrderTransitions.resolveTarget(from, event);
        if (updateMedTechStatusIfCurrent(kind, orderId, from, to) == 0) {
            assertMedTechMismatch(from, event, mismatchHint);
        }
    }

    private void applyPrescriptionTransition(Long prescriptionId, PrescriptionEvent event, String mismatchHint) {
        int from = currentPrescriptionStatus(prescriptionId);
        int to = PrescriptionTransitions.resolveTarget(from, event);
        if (prescriptionRepository.updateStatusIfCurrent(prescriptionId, from, to) == 0) {
            assertPrescriptionMismatch(from, event, mismatchHint);
        }
    }

    private int currentMedTechStatus(MedTechOrderKind kind, Long orderId) {
        Map<String, Object> row = findMedTechForUpdate(kind, orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "医嘱不存在"));
        return ((Number) row.get("status")).intValue();
    }

    private int currentPrescriptionStatus(Long prescriptionId) {
        Map<String, Object> row = prescriptionRepository.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));
        return ((Number) row.get("status")).intValue();
    }

    private java.util.Optional<Map<String, Object>> findMedTechForUpdate(MedTechOrderKind kind, Long orderId) {
        return switch (kind) {
            case INSPECTION -> inspectionRequestRepository.findByIdForUpdate(orderId);
            case CHECK -> checkRequestRepository.findByIdForUpdate(orderId);
            case DISPOSAL -> disposalRequestRepository.findByIdForUpdate(orderId);
        };
    }

    private int updateMedTechStatusIfCurrent(MedTechOrderKind kind, Long orderId, int from, int to) {
        return switch (kind) {
            case INSPECTION -> inspectionRequestRepository.updateStatusIfCurrent(orderId, from, to);
            case CHECK -> checkRequestRepository.updateStatusIfCurrent(orderId, from, to);
            case DISPOSAL -> disposalRequestRepository.updateStatusIfCurrent(orderId, from, to);
        };
    }

    private void assertMedTechMismatch(int from, MedTechOrderEvent event, String hint) {
        try {
            MedTechOrderTransitions.resolveTarget(from, event);
        } catch (MedTechOrderTransitionException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, hint);
        }
        throw concurrentMedTech(event);
    }

    private void assertPrescriptionMismatch(int from, PrescriptionEvent event, String hint) {
        try {
            PrescriptionTransitions.resolveTarget(from, event);
        } catch (PrescriptionTransitionException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, hint);
        }
        throw concurrentPrescription(event);
    }

    private BusinessException mismatchAfterStock(PrescriptionEvent event, String hint) {
        return new BusinessException(ErrorCode.BAD_REQUEST, hint + "（库存已变更，请联系管理员）");
    }

    private BusinessException concurrentMedTech(MedTechOrderEvent event) {
        return new BusinessException(ErrorCode.BAD_REQUEST, "医嘱状态已变更，请刷新后重试（event=" + event + "）");
    }

    private BusinessException concurrentPrescription(PrescriptionEvent event) {
        return new BusinessException(ErrorCode.BAD_REQUEST, "处方状态已变更，请刷新后重试（event=" + event + "）");
    }
}
