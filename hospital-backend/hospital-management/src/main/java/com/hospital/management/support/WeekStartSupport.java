package com.hospital.management.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class WeekStartSupport {

    private WeekStartSupport() {
    }

    public static LocalDate alignToMonday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate weekEnd(LocalDate weekStart) {
        return weekStart.plusDays(6);
    }

    /** ISO weekday: Mon=1 … Sun=7 */
    public static int toWeekday(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    public static LocalDate dateForWeekday(LocalDate weekStart, int weekday) {
        return weekStart.plusDays(weekday - 1L);
    }
}
