package com.hospital.management.support;

public final class RegistLevelQuotaDefaults {

    public static final int NORMAL_QUOTA = 30;
    public static final int EXPERT_QUOTA = 15;
    public static final long NORMAL_LEVEL_ID = 1L;
    public static final long EXPERT_LEVEL_ID = 2L;

    private RegistLevelQuotaDefaults() {
    }

    public static int defaultQuota(Long registLevelId) {
        if (registLevelId == null) {
            return NORMAL_QUOTA;
        }
        return registLevelId == EXPERT_LEVEL_ID ? EXPERT_QUOTA : NORMAL_QUOTA;
    }
}
