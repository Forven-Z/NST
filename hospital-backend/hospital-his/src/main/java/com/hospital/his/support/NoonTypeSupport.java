package com.hospital.his.support;

import java.time.LocalTime;
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

    /** 窗口挂号：按当前时刻判定午别（13:00 前上午，18:00 前下午，否则晚上）。 */
    public static int resolveCurrentNoonType(LocalTime now) {
        if (now.isBefore(LocalTime.of(13, 0))) {
            return 1;
        }
        if (now.isBefore(LocalTime.of(18, 0))) {
            return 2;
        }
        return 3;
    }

    /** 窗口挂号号源可见：slot 午别 >= 当前午别（上午含下午/晚上，下午含晚上，晚上仅晚上）。 */
    public static boolean visibleForWindowRegister(int slotNoonType, int currentNoonType) {
        return slotNoonType >= currentNoonType;
    }
}
