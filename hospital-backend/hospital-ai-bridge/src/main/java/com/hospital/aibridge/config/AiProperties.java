package com.hospital.aibridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hospital.ai")
public class AiProperties {

    private boolean enabled = false;
    private int maxRounds = 5;
    private int maxFollowUpRounds = 3;
    private String safetyNotice = "本结果仅用于就诊分诊参考，不能替代医生诊断。如症状严重或持续加重，请及时就医。";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getMaxFollowUpRounds() {
        return maxFollowUpRounds;
    }

    public void setMaxFollowUpRounds(int maxFollowUpRounds) {
        this.maxFollowUpRounds = maxFollowUpRounds;
    }

    public String getSafetyNotice() {
        return safetyNotice;
    }

    public void setSafetyNotice(String safetyNotice) {
        this.safetyNotice = safetyNotice;
    }
}
