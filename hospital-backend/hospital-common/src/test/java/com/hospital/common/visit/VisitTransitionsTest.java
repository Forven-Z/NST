package com.hospital.common.visit;

import com.hospital.common.constant.VisitState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisitTransitionsTest {

    @Test
    void payRegistrationFromPendingPayment() {
        assertEquals(VisitState.REGISTERED,
                VisitTransitions.resolveTarget(VisitState.PENDING_PAYMENT, VisitEvent.PAY_REGISTRATION));
    }

    @Test
    void cancelPendingPaths() {
        assertEquals(VisitState.CANCELLED,
                VisitTransitions.resolveTarget(VisitState.PENDING_PAYMENT, VisitEvent.CANCEL_PENDING));
        assertEquals(VisitState.CANCELLED,
                VisitTransitions.resolveTarget(VisitState.PENDING_PAYMENT, VisitEvent.EXPIRE_PENDING));
    }

    @Test
    void callFromRegistered() {
        assertEquals(VisitState.IN_CONSULTATION,
                VisitTransitions.resolveTarget(VisitState.REGISTERED, VisitEvent.CALL));
    }

    @Test
    void cancelRegisteredFromRegisteredOnly() {
        assertEquals(VisitState.CANCELLED,
                VisitTransitions.resolveTarget(VisitState.REGISTERED, VisitEvent.CANCEL_REGISTERED));
    }

    @Test
    void finishFromInConsultation() {
        assertEquals(VisitState.FINISHED,
                VisitTransitions.resolveTarget(VisitState.IN_CONSULTATION, VisitEvent.FINISH));
    }

    @Test
    void autoDayCloseFromRegisteredOrInConsultation() {
        assertEquals(VisitState.FINISHED,
                VisitTransitions.resolveTarget(VisitState.REGISTERED, VisitEvent.AUTO_DAY_CLOSE));
        assertEquals(VisitState.FINISHED,
                VisitTransitions.resolveTarget(VisitState.IN_CONSULTATION, VisitEvent.AUTO_DAY_CLOSE));
    }

    @Test
    void rejectCallFromInConsultation() {
        assertThrows(VisitTransitionException.class,
                () -> VisitTransitions.resolveTarget(VisitState.IN_CONSULTATION, VisitEvent.CALL));
    }

    @Test
    void rejectFinishFromRegistered() {
        assertThrows(VisitTransitionException.class,
                () -> VisitTransitions.resolveTarget(VisitState.REGISTERED, VisitEvent.FINISH));
    }

    @Test
    void rejectPayFromRegistered() {
        assertThrows(VisitTransitionException.class,
                () -> VisitTransitions.resolveTarget(VisitState.REGISTERED, VisitEvent.PAY_REGISTRATION));
    }

    @Test
    void rejectCancelRegisteredFromInConsultation() {
        assertThrows(VisitTransitionException.class,
                () -> VisitTransitions.resolveTarget(VisitState.IN_CONSULTATION, VisitEvent.CANCEL_REGISTERED));
    }

    @ParameterizedTest
    @EnumSource(VisitEvent.class)
    void terminalStatesRejectAllEvents(VisitEvent event) {
        assertFalse(VisitTransitions.canTransition(VisitState.FINISHED, event));
        assertFalse(VisitTransitions.canTransition(VisitState.CANCELLED, event));
        assertThrows(VisitTransitionException.class,
                () -> VisitTransitions.resolveTarget(VisitState.FINISHED, event));
        assertThrows(VisitTransitionException.class,
                () -> VisitTransitions.resolveTarget(VisitState.CANCELLED, event));
    }
}
