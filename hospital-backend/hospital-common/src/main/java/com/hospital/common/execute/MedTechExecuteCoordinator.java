package com.hospital.common.execute;

/**
 * 医技 execute / saveResult 状态迁移（步骤 ④，由各模块 Coordinator 实现）。
 */
public interface MedTechExecuteCoordinator {

    void execute(Long orderId, Long executorId);

    int resolveSaveResultTarget(int from, boolean signAsReviewerOnly, boolean pendingReview);

    void applySaveResultStatus(Long orderId, int from, int targetStatus);
}
