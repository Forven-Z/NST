package com.hospital.common.visit;

import com.hospital.common.constant.VisitState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitStateLabelsTest {

    @Test
    void label_mapsKnownStates() {
        assertEquals("待支付", VisitStateLabels.label(VisitState.PENDING_PAYMENT));
        assertEquals("已挂号", VisitStateLabels.label(VisitState.REGISTERED));
        assertEquals("接诊中", VisitStateLabels.label(VisitState.IN_CONSULTATION));
        assertEquals("看诊结束", VisitStateLabels.label(VisitState.FINISHED));
        assertEquals("已退号", VisitStateLabels.label(VisitState.CANCELLED));
    }

    @Test
    void label_unknownState() {
        assertEquals("未知", VisitStateLabels.label(99));
    }
}
