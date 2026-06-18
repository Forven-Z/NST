package com.hospital.aibridge.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DoctorAiDraftUpdateRequest {

    private String aiReason;
    private List<Map<String, Object>> items;
    private Map<String, Object> finalContent;
}
