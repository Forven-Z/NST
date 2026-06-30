package com.hospital.common.order;

import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;

/**
 * 医技 saveResult 后的目标 status（可能含 EXECUTE + RESULT_READY 复合语义）。
 */
public final class MedTechOrderSaveResultSupport {

    private MedTechOrderSaveResultSupport() {
    }

    public static void assertCanSaveResult(int from, boolean signAsReviewerOnly) {
        if (signAsReviewerOnly) {
            if (from < InspectionRequestStatus.EXECUTED) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "报告尚未录入，无法审核");
            }
            return;
        }
        if (from != InspectionRequestStatus.PAID
                && from != InspectionRequestStatus.EXECUTED
                && from != InspectionRequestStatus.RESULT_READY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可录入结果");
        }
    }

    /**
     * @return 落库目标 status
     */
    public static int resolveNextStatus(int from, boolean signAsReviewerOnly, boolean pendingReview) {
        assertCanSaveResult(from, signAsReviewerOnly);
        if (signAsReviewerOnly) {
            if (from == InspectionRequestStatus.RESULT_READY) {
                return from;
            }
            return MedTechOrderTransitions.resolveTarget(from, MedTechOrderEvent.RESULT_READY);
        }
        if (from == InspectionRequestStatus.PAID) {
            int executed = MedTechOrderTransitions.resolveTarget(from, MedTechOrderEvent.EXECUTE);
            if (pendingReview) {
                return executed;
            }
            return MedTechOrderTransitions.resolveTarget(executed, MedTechOrderEvent.RESULT_READY);
        }
        if (from == InspectionRequestStatus.EXECUTED) {
            if (pendingReview) {
                return InspectionRequestStatus.EXECUTED;
            }
            return MedTechOrderTransitions.resolveTarget(from, MedTechOrderEvent.RESULT_READY);
        }
        return from;
    }
}
