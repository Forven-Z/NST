package com.hospital.management.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegistLevelQuotaDefaultsTest {

    @Test
    void defaultQuota_expertIs15() {
        assertEquals(15, RegistLevelQuotaDefaults.defaultQuota(2L));
    }

    @Test
    void defaultQuota_normalIs30() {
        assertEquals(30, RegistLevelQuotaDefaults.defaultQuota(1L));
    }
}
