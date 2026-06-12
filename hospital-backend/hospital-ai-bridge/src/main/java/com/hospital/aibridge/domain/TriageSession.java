package com.hospital.aibridge.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TriageSession {

    private final String sessionId;
    private final Long patientId;
    private final List<TriageMessage> messages = new ArrayList<>();
    private String summary = "";
    private TriageStage stage = TriageStage.ASKING;
    private final Instant createTime;
    private Instant updateTime;

    public TriageSession(String sessionId, Long patientId) {
        this.sessionId = sessionId;
        this.patientId = patientId;
        this.createTime = Instant.now();
        this.updateTime = this.createTime;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public List<TriageMessage> getMessages() {
        return messages;
    }

    public int getRound() {
        return (int) messages.stream().filter(message -> "user".equals(message.role())).count();
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public TriageStage getStage() {
        return stage;
    }

    public void setStage(TriageStage stage) {
        this.stage = stage;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void touch() {
        this.updateTime = Instant.now();
    }
}
