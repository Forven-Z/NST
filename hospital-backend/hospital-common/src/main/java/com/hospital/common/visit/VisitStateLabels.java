package com.hospital.common.visit;

import com.hospital.common.constant.VisitState;

/**
 * 就诊状态展示文案（医生队列、收费员、患者端、Hub 共用）。
 */
public final class VisitStateLabels {

    private VisitStateLabels() {
    }

    public static String label(int visitState) {
        return switch (visitState) {
            case VisitState.PENDING_PAYMENT -> "待支付";
            case VisitState.REGISTERED -> "已挂号";
            case VisitState.IN_CONSULTATION -> "接诊中";
            case VisitState.FINISHED -> "看诊结束";
            case VisitState.CANCELLED -> "已退号";
            default -> "未知";
        };
    }
}
