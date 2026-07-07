package com.hospital.patient.support;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class NoonTypeSupportTest {

    @Test
    void label_knownNoonTypes() {
        assertThat(NoonTypeSupport.label(1)).isEqualTo("上午");
        assertThat(NoonTypeSupport.label(2)).isEqualTo("下午");
        assertThat(NoonTypeSupport.label(3)).isEqualTo("晚上");
        assertThat(NoonTypeSupport.label(9)).isEqualTo("—");
    }

    @Test
    void timeRange_knownNoonTypes() {
        assertThat(NoonTypeSupport.timeRange(1)).isEqualTo("08:00-12:00");
        assertThat(NoonTypeSupport.timeRange(2)).isEqualTo("13:00-17:00");
        assertThat(NoonTypeSupport.timeRange(3)).isEqualTo("18:00-21:00");
    }

    @Test
    void resolveCurrentNoonType_morning() {
        assertThat(NoonTypeSupport.resolveCurrentNoonType(LocalTime.of(10, 0))).isEqualTo(1);
    }

    @Test
    void resolveCurrentNoonType_afternoon() {
        assertThat(NoonTypeSupport.resolveCurrentNoonType(LocalTime.of(15, 0))).isEqualTo(2);
    }

    @Test
    void resolveCurrentNoonType_evening() {
        assertThat(NoonTypeSupport.resolveCurrentNoonType(LocalTime.of(20, 0))).isEqualTo(3);
    }

    @Test
    void visibleForWindowRegister_allowsLaterSlots() {
        assertThat(NoonTypeSupport.visibleForWindowRegister(2, 1)).isTrue();
        assertThat(NoonTypeSupport.visibleForWindowRegister(3, 2)).isTrue();
        assertThat(NoonTypeSupport.visibleForWindowRegister(1, 2)).isFalse();
    }
}
