package com.hospital.aibridge.dto;

import lombok.Data;

@Data
public class TriageChatRequest {

    private Long patientId;
    private Long registerId;
    private String message;
    private String sessionId;
}
