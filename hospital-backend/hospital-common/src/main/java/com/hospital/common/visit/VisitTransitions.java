package com.hospital.common.visit;

import com.hospital.common.constant.VisitState;

import java.util.Map;

/**
 * {@code register.visit_state} 转换表（唯一真相）。
 * <p>
 * 与 {@code BUSINESS_FLOW.md §8.1}、{@code REFACTORING_DESIGN_PATTERNS.md §4.1} 一致。
 */
public final class VisitTransitions {

    private static final Map<TransitionKey, Integer> TARGETS = Map.ofEntries(
            Map.entry(key(VisitState.PENDING_PAYMENT, VisitEvent.PAY_REGISTRATION), VisitState.REGISTERED),
            Map.entry(key(VisitState.PENDING_PAYMENT, VisitEvent.CANCEL_PENDING), VisitState.CANCELLED),
            Map.entry(key(VisitState.PENDING_PAYMENT, VisitEvent.EXPIRE_PENDING), VisitState.CANCELLED),
            Map.entry(key(VisitState.REGISTERED, VisitEvent.CALL), VisitState.IN_CONSULTATION),
            Map.entry(key(VisitState.REGISTERED, VisitEvent.CANCEL_REGISTERED), VisitState.CANCELLED),
            Map.entry(key(VisitState.REGISTERED, VisitEvent.AUTO_DAY_CLOSE), VisitState.FINISHED),
            Map.entry(key(VisitState.IN_CONSULTATION, VisitEvent.FINISH), VisitState.FINISHED),
            Map.entry(key(VisitState.IN_CONSULTATION, VisitEvent.AUTO_DAY_CLOSE), VisitState.FINISHED)
    );

    private VisitTransitions() {
    }

    /**
     * @return 迁移后的目标状态
     * @throws VisitTransitionException 当前状态不允许该事件
     */
    public static int resolveTarget(int fromState, VisitEvent event) {
        Integer target = TARGETS.get(new TransitionKey(fromState, event));
        if (target == null) {
            throw new VisitTransitionException(fromState, event);
        }
        return target;
    }

    public static void assertTransition(int fromState, VisitEvent event) {
        resolveTarget(fromState, event);
    }

    public static boolean canTransition(int fromState, VisitEvent event) {
        return TARGETS.containsKey(new TransitionKey(fromState, event));
    }

    private static TransitionKey key(int fromState, VisitEvent event) {
        return new TransitionKey(fromState, event);
    }

    private record TransitionKey(int fromState, VisitEvent event) {
    }
}
