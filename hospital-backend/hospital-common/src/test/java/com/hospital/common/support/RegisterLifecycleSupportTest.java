package com.hospital.common.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterLifecycleSupportTest {

    @Test
    void paidRegisterCancellableOnlyWhenNotCalled() {
        assertTrue(RegisterLifecycleSupport.isPaidRegisterCancellable(1, null));
        assertFalse(RegisterLifecycleSupport.isPaidRegisterCancellable(1, OffsetDateTime.now()));
        assertFalse(RegisterLifecycleSupport.isPaidRegisterCancellable(2, OffsetDateTime.now()));
    }

    @Test
    void pendingPaymentExpiresAfterTenMinutes() {
        OffsetDateTime created = OffsetDateTime.of(2026, 6, 4, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        assertFalse(RegisterLifecycleSupport.isPendingPaymentExpired(created, created.plusMinutes(9)));
        assertTrue(RegisterLifecycleSupport.isPendingPaymentExpired(created, created.plusMinutes(10)));
    }

    @Test
    void dayCloseDueAfterNinePmOrPastDate() {
        LocalDate today = LocalDate.of(2026, 6, 4);
        assertTrue(RegisterLifecycleSupport.isDayCloseDue(today.minusDays(1), today, LocalTime.of(10, 0)));
        assertFalse(RegisterLifecycleSupport.isDayCloseDue(today, today, LocalTime.of(20, 59)));
        assertTrue(RegisterLifecycleSupport.isDayCloseDue(today, today, LocalTime.of(21, 0)));
    }
}
