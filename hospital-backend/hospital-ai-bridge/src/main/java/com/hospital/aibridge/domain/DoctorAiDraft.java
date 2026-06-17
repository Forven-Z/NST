package com.hospital.aibridge.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class DoctorAiDraft {

    private Long id;
    private Long auditDraftId;
    private Long registerId;
    private String draftType;
    private String aiReason;
    private List<Map<String, Object>> originalItems;
    private List<Map<String, Object>> editedItems;
    private Map<String, Object> finalContent;
    private int status;
    private OffsetDateTime createTime;
    private OffsetDateTime updateTime;
    private OffsetDateTime confirmTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuditDraftId() {
        return auditDraftId;
    }

    public void setAuditDraftId(Long auditDraftId) {
        this.auditDraftId = auditDraftId;
    }

    public Long getRegisterId() {
        return registerId;
    }

    public void setRegisterId(Long registerId) {
        this.registerId = registerId;
    }

    public String getDraftType() {
        return draftType;
    }

    public void setDraftType(String draftType) {
        this.draftType = draftType;
    }

    public String getAiReason() {
        return aiReason;
    }

    public void setAiReason(String aiReason) {
        this.aiReason = aiReason;
    }

    public List<Map<String, Object>> getOriginalItems() {
        return originalItems;
    }

    public void setOriginalItems(List<Map<String, Object>> originalItems) {
        this.originalItems = originalItems;
    }

    public List<Map<String, Object>> getEditedItems() {
        return editedItems;
    }

    public void setEditedItems(List<Map<String, Object>> editedItems) {
        this.editedItems = editedItems;
    }

    public Map<String, Object> getFinalContent() {
        return finalContent;
    }

    public void setFinalContent(Map<String, Object> finalContent) {
        this.finalContent = finalContent;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }

    public OffsetDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(OffsetDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public OffsetDateTime getConfirmTime() {
        return confirmTime;
    }

    public void setConfirmTime(OffsetDateTime confirmTime) {
        this.confirmTime = confirmTime;
    }
}
