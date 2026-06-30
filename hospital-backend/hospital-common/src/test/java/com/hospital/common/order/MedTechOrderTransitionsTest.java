package com.hospital.common.order;

import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.PrescriptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedTechOrderTransitionsTest {

    @Test
    void payFromOrdered() {
        assertEquals(InspectionRequestStatus.PAID,
                MedTechOrderTransitions.resolveTarget(InspectionRequestStatus.ORDERED, MedTechOrderEvent.PAY));
    }

    @Test
    void executeFromPaid() {
        assertEquals(InspectionRequestStatus.EXECUTED,
                MedTechOrderTransitions.resolveTarget(InspectionRequestStatus.PAID, MedTechOrderEvent.EXECUTE));
    }

    @Test
    void resultReadyFromExecuted() {
        assertEquals(InspectionRequestStatus.RESULT_READY,
                MedTechOrderTransitions.resolveTarget(InspectionRequestStatus.EXECUTED,
                        MedTechOrderEvent.RESULT_READY));
    }

    @Test
    void refundFromPaidOnly() {
        assertEquals(InspectionRequestStatus.REFUNDED,
                MedTechOrderTransitions.resolveTarget(InspectionRequestStatus.PAID, MedTechOrderEvent.REFUND));
    }

    @Test
    void rejectExecuteFromOrdered() {
        assertThrows(MedTechOrderTransitionException.class,
                () -> MedTechOrderTransitions.resolveTarget(InspectionRequestStatus.ORDERED,
                        MedTechOrderEvent.EXECUTE));
    }

    @Test
    void rejectPayFromPaid() {
        assertThrows(MedTechOrderTransitionException.class,
                () -> MedTechOrderTransitions.resolveTarget(InspectionRequestStatus.PAID, MedTechOrderEvent.PAY));
    }

    @ParameterizedTest
    @EnumSource(MedTechOrderEvent.class)
    void terminalRefundedRejectsAll(MedTechOrderEvent event) {
        assertFalse(MedTechOrderTransitions.canTransition(InspectionRequestStatus.REFUNDED, event));
    }
}
