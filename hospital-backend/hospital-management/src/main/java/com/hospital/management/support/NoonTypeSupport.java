package com.hospital.management.support;

import java.util.Map;

public final class NoonTypeSupport {

    private static final Map<Integer, String> LABEL = Map.of(1, "上午", 2, "下午", 3, "晚上");
    private static final Map<Integer, String> RANGE = Map.of(1, "08:00-12:00", 2, "13:00-17:00", 3, "18:00-21:00");

    private NoonTypeSupport() {
    }

    public static String label(int noonType) {
        return LABEL.getOrDefault(noonType, "—");
    }

    public static String timeRange(int noonType) {
        return RANGE.getOrDefault(noonType, "");
    }
}
