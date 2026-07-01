package com.hospital.patient.util;

import java.time.LocalDate;
import java.time.Period;

/** 身份证号解析（18 位） */
public final class IdCardUtils {

    private IdCardUtils() {
    }

    public static String normalizeIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) {
            return null;
        }
        return idCard.trim().toUpperCase();
    }

    public static LocalDate parseBirthDate(String idCard) {
        String normalized = normalizeIdCard(idCard);
        if (normalized == null || !normalized.matches("^\\d{17}[\\dX]$")) {
            return null;
        }
        try {
            int year = Integer.parseInt(normalized.substring(6, 10));
            int month = Integer.parseInt(normalized.substring(10, 12));
            int day = Integer.parseInt(normalized.substring(12, 14));
            return LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 窗口挂号：优先 birthDate；否则从身份证解析；否则由年龄推算出生日期（周岁近似）。
     */
    public static LocalDate resolveBirthDate(LocalDate birthDate, Integer age, String idCard) {
        if (birthDate != null) {
            return birthDate;
        }
        LocalDate fromIdCard = parseBirthDate(idCard);
        if (fromIdCard != null) {
            return fromIdCard;
        }
        if (age != null && age >= 0 && age <= 150) {
            return LocalDate.now().minusYears(age);
        }
        return null;
    }

    /** 优先使用表单年龄；否则由 birthDate 推算周岁。 */
    public static Integer resolveAge(Integer age, LocalDate birthDate) {
        if (age != null && age >= 0 && age <= 150) {
            return age;
        }
        if (birthDate != null) {
            return Period.between(birthDate, LocalDate.now()).getYears();
        }
        return null;
    }
}
