package com.hospital.lis.order;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.order.MedTechOrderEvent;
import com.hospital.common.order.MedTechOrderTransitionException;
import com.hospital.common.order.MedTechOrderTransitions;
import com.hospital.lis.repository.InspectionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LisMedTechOrderCoordinator {

    private final InspectionRequestRepository inspectionRequestRepository;

    @Transactional
    public void execute(Long inspectionRequestId, Long executorId) {
        int from = currentStatus(inspectionRequestId);
        MedTechOrderTransitions.assertTransition(from, MedTechOrderEvent.EXECUTE);
        if (inspectionRequestRepository.markExecutedIfCurrent(inspectionRequestId, from, executorId) == 0) {
            assertMismatch(from, MedTechOrderEvent.EXECUTE, "仅已缴费申请可执行");
        }
    }

    /** 校验 saveResult 目标 status 并返回（含复合 EXECUTE→RESULT_READY 语义）。 */
    public int resolveSaveResultTarget(int from, boolean signAsReviewerOnly, boolean pendingReview) {
        return com.hospital.common.order.MedTechOrderSaveResultSupport.resolveNextStatus(
                from, signAsReviewerOnly, pendingReview);
    }

    @Transactional
    public void applySaveResultStatus(Long inspectionRequestId, int from, int targetStatus) {
        if (targetStatus == from) {
            return;
        }
        if (from == InspectionRequestStatus.PAID && targetStatus == InspectionRequestStatus.EXECUTED) {
            transition(inspectionRequestId, from, MedTechOrderEvent.EXECUTE);
            return;
        }
        if (from == InspectionRequestStatus.PAID && targetStatus == InspectionRequestStatus.RESULT_READY) {
            transition(inspectionRequestId, from, MedTechOrderEvent.EXECUTE);
            transition(inspectionRequestId, InspectionRequestStatus.EXECUTED, MedTechOrderEvent.RESULT_READY);
            return;
        }
        if (from == InspectionRequestStatus.EXECUTED && targetStatus == InspectionRequestStatus.RESULT_READY) {
            transition(inspectionRequestId, from, MedTechOrderEvent.RESULT_READY);
            return;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可迁移至目标状态");
    }

    private void transition(Long id, int from, MedTechOrderEvent event) {
        int to = MedTechOrderTransitions.resolveTarget(from, event);
        if (inspectionRequestRepository.updateStatusIfCurrent(id, from, to) == 0) {
            assertMismatch(from, event, "医嘱状态已变更，请刷新后重试");
        }
    }

    private int currentStatus(Long id) {
        Map<String, Object> row = inspectionRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));
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
