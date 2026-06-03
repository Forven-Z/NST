package com.hospital.common.constant;

/**
 * 检验申请状态：10 已开立 → 20 已缴费 → 30 执行完成 → 40 已出结果。
 */
public final class InspectionRequestStatus {

    public static final int ORDERED = 10;
    public static final int PAID = 20;
    public static final int EXECUTED = 30;
    public static final int RESULT_READY = 40;
    public static final int REFUNDED = 50;

    private InspectionRequestStatus() {
    }
}
