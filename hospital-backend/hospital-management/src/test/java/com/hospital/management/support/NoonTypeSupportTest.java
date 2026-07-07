package com.hospital.management.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoonTypeSupportTest {

    @Test
    void label_knownNoonTypes() {
        assertThat(NoonTypeSupport.label(1)).isEqualTo("上午");
        assertThat(NoonTypeSupport.label(2)).isEqualTo("下午");
        assertThat(NoonTypeSupport.label(3)).isEqualTo("晚上");
        assertThat(NoonTypeSupport.label(0)).isEqualTo("—");
    }

    @Test
    void timeRange_knownNoonTypes() {
        assertThat(NoonTypeSupport.timeRange(1)).isEqualTo("08:00-12:00");
        assertThat(NoonTypeSupport.timeRange(2)).isEqualTo("13:00-17:00");
        assertThat(NoonTypeSupport.timeRange(3)).isEqualTo("18:00-21:00");
        assertThat(NoonTypeSupport.timeRange(99)).isEmpty();
    }
}
