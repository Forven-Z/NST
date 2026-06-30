package com.hospital.disposal.order;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.order.MedTechOrderEvent;
import com.hospital.common.order.MedTechOrderTransitionException;
import com.hospital.common.order.MedTechOrderTransitions;
import com.hospital.disposal.repository.DisposalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisposalMedTechOrderCoordinator {

    private final DisposalRequestRepository disposalRequestRepository;

    @Transactional
    public void execute(Long disposalRequestId, Long executorId) {
        int from = currentStatus(disposalRequestId);
        MedTechOrderTransitions.assertTransition(from, MedTechOrderEvent.EXECUTE);
        if (disposalRequestRepository.markExecutedIfCurrent(disposalRequestId, from, executorId) == 0) {
            assertMismatch(from, MedTechOrderEvent.EXECUTE, "仅已缴费申请可执行");
        }
    }

    public int resolveSaveResultTarget(int from, boolean signAsReviewerOnly, boolean pendingReview) {
        return com.hospital.common.order.MedTechOrderSaveResultSupport.resolveNextStatus(
                from, signAsReviewerOnly, pendingReview);
    }

    @Transactional
    public void applySaveResultStatus(Long disposalRequestId, int from, int targetStatus) {
        if (targetStatus == from) {
            return;
        }
        if (from == InspectionRequestStatus.PAID && targetStatus == InspectionRequestStatus.EXECUTED) {
            transition(disposalRequestId, from, MedTechOrderEvent.EXECUTE);
            return;
        }
        if (from == InspectionRequestStatus.PAID && targetStatus == InspectionRequestStatus.RESULT_READY) {
            transition(disposalRequestId, from, MedTechOrderEvent.EXECUTE);
            transition(disposalRequestId, InspectionRequestStatus.EXECUTED, MedTechOrderEvent.RESULT_READY);
            return;
        }
        if (from == InspectionRequestStatus.EXECUTED && targetStatus == InspectionRequestStatus.RESULT_READY) {
            transition(disposalRequestId, from, MedTechOrderEvent.RESULT_READY);
        }
    }

    private void transition(Long id, int from, MedTechOrderEvent event) {
        int to = MedTechOrderTransitions.resolveTarget(from, event);
        if (disposalRequestRepository.updateStatusIfCurrent(id, from, to) == 0) {
            assertMismatch(from, event, "医嘱状态已变更，请刷新后重试");
        }
    }

    private int currentStatus(Long id) {
        Map<String, Object> row = disposalRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));
        return ((Number) row.get("status")).intValue();
    }

    private void assertMismatch(int from, MedTechOrderEvent event, String hint) {
        try {
            MedTechOrderTransitions.resolveTarget(from, event);
        } catch (MedTechOrderTransitionException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, hint);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "医嘱状态已变更，请刷新后重试");
    }
}
