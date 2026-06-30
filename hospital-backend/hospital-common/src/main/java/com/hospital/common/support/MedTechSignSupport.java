package com.hospital.common.support;

/**
 * 医技报告签名人解析：单签（录入=审核）与双签（先录入、后审核）。
 */
public final class MedTechSignSupport {

    public record SignAssignment(Long reporterId, Long reviewerId, boolean pendingReview) {}

    private MedTechSignSupport() {}

    /**
     * @param signAsReviewerOnly  true 时仅更新审核人（双签第二步）
     * @param pendingReview       true 时仅写入报告人，审核人留空（双签第一步）
     * @param existingReporterId  已有报告人（双签第二步必填）
     */
    public static SignAssignment resolve(
            Long currentEmployeeId,
            Boolean signAsReviewerOnly,
            Boolean pendingReview,
            Long existingReporterId) {

        if (currentEmployeeId == null) {
            throw new IllegalArgumentException("当前职员 ID 不能为空");
        }

        if (Boolean.TRUE.equals(signAsReviewerOnly)) {
            if (existingReporterId == null) {
                throw new IllegalArgumentException("尚未录入报告，无法审核签阅");
            }
            return new SignAssignment(existingReporterId, currentEmployeeId, false);
        }

        if (Boolean.TRUE.equals(pendingReview)) {
            return new SignAssignment(currentEmployeeId, null, true);
        }

        return new SignAssignment(currentEmployeeId, currentEmployeeId, false);
    }
}
