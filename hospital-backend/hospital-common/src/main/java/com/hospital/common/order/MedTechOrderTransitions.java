package com.hospital.common.order;

import com.hospital.common.constant.InspectionRequestStatus;

import java.util.Map;

/**
 * 检验/检查/处置共用转换表（SM1）。
 */
public final class MedTechOrderTransitions {

    private static final Map<TransitionKey, Integer> TARGETS = Map.ofEntries(
            Map.entry(key(InspectionRequestStatus.ORDERED, MedTechOrderEvent.PAY), InspectionRequestStatus.PAID),
            Map.entry(key(InspectionRequestStatus.PAID, MedTechOrderEvent.EXECUTE), InspectionRequestStatus.EXECUTED),
            Map.entry(key(InspectionRequestStatus.EXECUTED, MedTechOrderEvent.RESULT_READY),
                    InspectionRequestStatus.RESULT_READY),
            Map.entry(key(InspectionRequestStatus.PAID, MedTechOrderEvent.REFUND), InspectionRequestStatus.REFUNDED)
    );

    private MedTechOrderTransitions() {
    }

    public static int resolveTarget(int fromState, MedTechOrderEvent event) {
        Integer target = TARGETS.get(new TransitionKey(fromState, event));
        if (target == null) {
            throw new MedTechOrderTransitionException(fromState, event);
        }
        return target;
    }

    public static void assertTransition(int fromState, MedTechOrderEvent event) {
        resolveTarget(fromState, event);
    }

    public static boolean canTransition(int fromState, MedTechOrderEvent event) {
        return TARGETS.containsKey(new TransitionKey(fromState, event));
    }

    /** 开立时写入的初始状态（无 from 态）。 */
    public static int orderedStatus() {
        return InspectionRequestStatus.ORDERED;
    }

    private static TransitionKey key(int fromState, MedTechOrderEvent event) {
        return new TransitionKey(fromState, event);
    }

    private record TransitionKey(int fromState, MedTechOrderEvent event) {
    }
}
