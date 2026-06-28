package com.hospital.management.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeekStartSupportTest {

    @Test
    void alignToMonday_whenWednesday_returnsPreviousMonday() {
        LocalDate wed = LocalDate.of(2026, 4, 1);
        assertEquals(LocalDate.of(2026, 3, 30), WeekStartSupport.alignToMonday(wed));
    }

    @Test
    void weekEnd_isSixDaysAfterWeekStart() {
        LocalDate mon = LocalDate.of(2026, 3, 30);
        assertEquals(LocalDate.of(2026, 4, 5), WeekStartSupport.weekEnd(mon));
    }

    @Test
    void toWeekday_isoMondayIs1() {
        assertEquals(1, WeekStartSupport.toWeekday(LocalDate.of(2026, 3, 30)));
    }
}
