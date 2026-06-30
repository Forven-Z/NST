package com.hospital.common.visit;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;

/**
 * 非法 {@code visit_state} 迁移；由全局异常处理器转为统一 {@code Result}。
 */
public class VisitTransitionException extends BusinessException {

    public VisitTransitionException(int fromState, VisitEvent event) {
        super(ErrorCode.BAD_REQUEST, formatMessage(fromState, event));
    }

    private static String formatMessage(int fromState, VisitEvent event) {
        return "非法就诊状态迁移: 当前=" + stateLabel(fromState) + "(" + fromState + "), 事件="
                + event.name();
    }

    private static String stateLabel(int state) {
        return switch (state) {
            case VisitState.PENDING_PAYMENT -> "待支付";
            case VisitState.REGISTERED -> "已挂号";
            case VisitState.IN_CONSULTATION -> "接诊中";
            case VisitState.FINISHED -> "看诊结束";
            case VisitState.CANCELLED -> "已退号";
            default -> "未知";
        };
    }
}
