package com.hospital.aibridge.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AssistantStreamRequest {

    private Long registerId;
    private String message;
    private Map<String, Object> context;
}
