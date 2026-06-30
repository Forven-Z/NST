package com.hospital.common.execute;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.order.MedTechOrderEvent;
import com.hospital.common.order.MedTechOrderSaveResultSupport;
import com.hospital.common.order.MedTechOrderTransitionException;
import com.hospital.common.order.MedTechOrderTransitions;

import java.util.Map;

/**
 * 医技 SM1 写库模板（步骤 ②/④ 共用）：execute 与 saveResult 复合迁移。
 */
public abstract class AbstractMedTechOrderCoordinator implements MedTechExecuteCoordinator {

    protected abstract MedTechOrderStatusWriter statusWriter();

    protected abstract String orderNotFoundMessage();

    protected abstract String executeMismatchHint();

    @Override
    public void execute(Long orderId, Long executorId) {
        int from = currentStatus(orderId);
        MedTechOrderTransitions.assertTransition(from, MedTechOrderEvent.EXECUTE);
        if (statusWriter().markExecutedIfCurrent(orderId, from, executorId) == 0) {
            assertMismatch(from, MedTechOrderEvent.EXECUTE, executeMismatchHint());
        }
    }

    @Override
    public int resolveSaveResultTarget(int from, boolean signAsReviewerOnly, boolean pendingReview) {
        return MedTechOrderSaveResultSupport.resolveNextStatus(from, signAsReviewerOnly, pendingReview);
    }

    @Override
    public void applySaveResultStatus(Long orderId, int from, int targetStatus) {
        if (targetStatus == from) {
            return;
        }
        if (from == InspectionRequestStatus.PAID && targetStatus == InspectionRequestStatus.EXECUTED) {
            transition(orderId, from, MedTechOrderEvent.EXECUTE);
            return;
        }
        if (from == InspectionRequestStatus.PAID && targetStatus == InspectionRequestStatus.RESULT_READY) {
            transition(orderId, from, MedTechOrderEvent.EXECUTE);
            transition(orderId, InspectionRequestStatus.EXECUTED, MedTechOrderEvent.RESULT_READY);
            return;
        }
        if (from == InspectionRequestStatus.EXECUTED && targetStatus == InspectionRequestStatus.RESULT_READY) {
            transition(orderId, from, MedTechOrderEvent.RESULT_READY);
            return;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可迁移至目标状态");
    }

    private void transition(Long orderId, int from, MedTechOrderEvent event) {
        int to = MedTechOrderTransitions.resolveTarget(from, event);
        if (statusWriter().updateStatusIfCurrent(orderId, from, to) == 0) {
            assertMismatch(from, event, "医嘱状态已变更，请刷新后重试");
        }
    }

    private int currentStatus(Long orderId) {
        Map<String, Object> row = statusWriter().findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, orderNotFoundMessage()));
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
