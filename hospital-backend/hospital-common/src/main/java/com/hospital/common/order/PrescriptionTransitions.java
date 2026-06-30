package com.hospital.common.order;

import com.hospital.common.constant.PrescriptionStatus;

import java.util.Map;

/**
 * 处方转换表（SM2）。
 */
public final class PrescriptionTransitions {

    private static final Map<TransitionKey, Integer> TARGETS = Map.ofEntries(
            Map.entry(key(PrescriptionStatus.ORDERED, PrescriptionEvent.PAY), PrescriptionStatus.PAID),
            Map.entry(key(PrescriptionStatus.PHARMACY_REJECTED, PrescriptionEvent.RESUBMIT),
                    PrescriptionStatus.ORDERED),
            Map.entry(key(PrescriptionStatus.PAID, PrescriptionEvent.PHARMACY_REJECT),
                    PrescriptionStatus.PHARMACY_REJECTED),
            Map.entry(key(PrescriptionStatus.PAID, PrescriptionEvent.DISPENSE), PrescriptionStatus.DISPENSED),
            Map.entry(key(PrescriptionStatus.DISPENSED, PrescriptionEvent.RETURN_DRUG), PrescriptionStatus.RETURNED),
            Map.entry(key(PrescriptionStatus.ORDERED, PrescriptionEvent.REFUND), PrescriptionStatus.REFUNDED),
            Map.entry(key(PrescriptionStatus.PAID, PrescriptionEvent.REFUND), PrescriptionStatus.REFUNDED),
            Map.entry(key(PrescriptionStatus.RETURNED, PrescriptionEvent.REFUND), PrescriptionStatus.REFUNDED)
    );

    private PrescriptionTransitions() {
    }

    public static int resolveTarget(int fromState, PrescriptionEvent event) {
        Integer target = TARGETS.get(new TransitionKey(fromState, event));
        if (target == null) {
            throw new PrescriptionTransitionException(fromState, event);
        }
        return target;
    }

    public static void assertTransition(int fromState, PrescriptionEvent event) {
        resolveTarget(fromState, event);
    }

    public static boolean canTransition(int fromState, PrescriptionEvent event) {
        return TARGETS.containsKey(new TransitionKey(fromState, event));
    }

    public static int orderedStatus() {
        return PrescriptionStatus.ORDERED;
    }

    /** 退费回库：仅 ORDERED/PAID 需要（RETURNED 已在退药时回库）。 */
    public static boolean restoreStockOnRefund(int fromState) {
        return fromState == PrescriptionStatus.ORDERED || fromState == PrescriptionStatus.PAID;
    }

    private static TransitionKey key(int fromState, PrescriptionEvent event) {
        return new TransitionKey(fromState, event);
    }

    private record TransitionKey(int fromState, PrescriptionEvent event) {
    }
}
