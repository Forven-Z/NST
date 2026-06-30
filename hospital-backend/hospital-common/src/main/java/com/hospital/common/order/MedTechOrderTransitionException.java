package com.hospital.common.order;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;

public class MedTechOrderTransitionException extends BusinessException {

    public MedTechOrderTransitionException(int fromState, MedTechOrderEvent event) {
        super(ErrorCode.BAD_REQUEST, formatMessage(fromState, event));
    }

    private static String formatMessage(int fromState, MedTechOrderEvent event) {
        return "非法医技医嘱状态迁移: 当前=" + statusLabel(fromState) + "(" + fromState + "), 事件="
                + event.name();
    }

    private static String statusLabel(int state) {
        return switch (state) {
            case InspectionRequestStatus.ORDERED -> "已开立";
            case InspectionRequestStatus.PAID -> "已缴费";
            case InspectionRequestStatus.EXECUTED -> "执行中";
            case InspectionRequestStatus.RESULT_READY -> "已出结果";
            case InspectionRequestStatus.REFUNDED -> "已退费";
            default -> "未知";
        };
    }
}
