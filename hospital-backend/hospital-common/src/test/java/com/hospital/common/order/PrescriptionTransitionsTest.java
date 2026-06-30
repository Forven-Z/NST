package com.hospital.common.order;

import com.hospital.common.constant.PrescriptionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrescriptionTransitionsTest {

    @Test
    void payAndDispenseFlow() {
        assertEquals(PrescriptionStatus.PAID,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.ORDERED, PrescriptionEvent.PAY));
        assertEquals(PrescriptionStatus.DISPENSED,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.PAID, PrescriptionEvent.DISPENSE));
    }

    @Test
    void rejectAndResubmit() {
        assertEquals(PrescriptionStatus.PHARMACY_REJECTED,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.PAID, PrescriptionEvent.PHARMACY_REJECT));
        assertEquals(PrescriptionStatus.ORDERED,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.PHARMACY_REJECTED,
                        PrescriptionEvent.RESUBMIT));
    }

    @Test
    void returnAndRefund() {
        assertEquals(PrescriptionStatus.RETURNED,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.DISPENSED,
                        PrescriptionEvent.RETURN_DRUG));
        assertEquals(PrescriptionStatus.REFUNDED,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.RETURNED, PrescriptionEvent.REFUND));
    }

    @Test
    void refundFromOrderedOrPaid() {
        assertEquals(PrescriptionStatus.REFUNDED,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.ORDERED, PrescriptionEvent.REFUND));
        assertEquals(PrescriptionStatus.REFUNDED,
                PrescriptionTransitions.resolveTarget(PrescriptionStatus.PAID, PrescriptionEvent.REFUND));
    }

    @Test
    void restoreStockOnRefundFlags() {
        assertTrue(PrescriptionTransitions.restoreStockOnRefund(PrescriptionStatus.ORDERED));
        assertTrue(PrescriptionTransitions.restoreStockOnRefund(PrescriptionStatus.PAID));
        assertFalse(PrescriptionTransitions.restoreStockOnRefund(PrescriptionStatus.RETURNED));
    }

    @Test
    void rejectDispenseFromOrdered() {
        assertThrows(PrescriptionTransitionException.class,
                () -> PrescriptionTransitions.resolveTarget(PrescriptionStatus.ORDERED,
                        PrescriptionEvent.DISPENSE));
    }
}
