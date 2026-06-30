package com.hospital.common.order;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.exception.BusinessException;

public class PrescriptionTransitionException extends BusinessException {

    public PrescriptionTransitionException(int fromState, PrescriptionEvent event) {
        super(ErrorCode.BAD_REQUEST, formatMessage(fromState, event));
    }

    private static String formatMessage(int fromState, PrescriptionEvent event) {
        return "非法处方状态迁移: 当前=" + statusLabel(fromState) + "(" + fromState + "), 事件="
                + event.name();
    }

    private static String statusLabel(int state) {
        return switch (state) {
            case PrescriptionStatus.ORDERED -> "已开立";
            case PrescriptionStatus.PHARMACY_REJECTED -> "药师驳回";
            case PrescriptionStatus.PAID -> "已缴费";
            case PrescriptionStatus.DISPENSED -> "已发药";
            case PrescriptionStatus.RETURNED -> "已退药";
            case PrescriptionStatus.REFUNDED -> "已退费";
            default -> "未知";
        };
    }
}
