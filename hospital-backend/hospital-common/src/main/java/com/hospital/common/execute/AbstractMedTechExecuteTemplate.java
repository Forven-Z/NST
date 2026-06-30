package com.hospital.common.execute;

import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.order.MedTechOrderSaveResultSupport;

import java.util.HashMap;
import java.util.Map;

/**
 * 医技 execute 模板（步骤 ④）：统一 20→30（SM1 EXECUTE）；saveResult 状态迁移经 Coordinator。
 */
public abstract class AbstractMedTechExecuteTemplate {

    protected abstract MedTechExecuteCoordinator coordinator();

    protected abstract Long requireExecutorId();

    protected abstract String orderIdResultKey();

    protected void onBeforeExecute(Long orderId) {
    }

    protected void onAfterExecute(Long orderId) {
    }

    protected Map<String, Object> executeOrder(Long orderId) {
        Long executorId = requireExecutorId();
        onBeforeExecute(orderId);
        coordinator().execute(orderId, executorId);
        onAfterExecute(orderId);

        Map<String, Object> result = new HashMap<>();
        result.put(orderIdResultKey(), orderId);
        result.put("status", InspectionRequestStatus.EXECUTED);
        return result;
    }

    protected void assertCanSaveResult(int from, boolean signAsReviewerOnly) {
        MedTechOrderSaveResultSupport.assertCanSaveResult(from, signAsReviewerOnly);
    }

    protected int resolveSaveResultTarget(int from, boolean signAsReviewerOnly, boolean pendingReview) {
        return coordinator().resolveSaveResultTarget(from, signAsReviewerOnly, pendingReview);
    }

    protected void applySaveResultStatus(Long orderId, int from, int targetStatus) {
        coordinator().applySaveResultStatus(orderId, from, targetStatus);
    }
}
