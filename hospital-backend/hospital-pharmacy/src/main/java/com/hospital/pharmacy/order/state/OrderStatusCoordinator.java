package com.hospital.pharmacy.order.state;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.order.PrescriptionEvent;
import com.hospital.common.order.PrescriptionTransitionException;
import com.hospital.common.order.PrescriptionTransitions;
import com.hospital.pharmacy.order.PrescriptionInventorySupport;
import com.hospital.pharmacy.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderStatusCoordinator {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionInventorySupport prescriptionInventorySupport;

    @Transactional
    public void pharmacyReject(Long prescriptionId, Long pharmacistId, String reason) {
        int from = currentPrescriptionStatus(prescriptionId);
        if (from == PrescriptionStatus.PHARMACY_REJECTED) {
            return;
        }
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

    private int currentPrescriptionStatus(Long prescriptionId) {
        Map<String, Object> row = prescriptionRepository.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));
        return ((Number) row.get("status")).intValue();
    }

    private void assertPrescriptionMismatch(int from, PrescriptionEvent event, String hint) {
        try {
            PrescriptionTransitions.resolveTarget(from, event);
        } catch (PrescriptionTransitionException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, hint);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "处方状态已变更，请刷新后重试（event=" + event + "）");
    }
}
