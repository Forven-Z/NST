package com.hospital.aibridge.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiStubService {

    public Map<String, Object> triageChat(String message, String sessionId) {
        return Map.of(
                "stub", true,
                "sessionId", sessionId != null ? sessionId : UUID.randomUUID().toString(),
                "delta", "【占位】您好，请先完成挂号后就诊。" + (message != null ? "" : "")
        );
    }

    public Map<String, Object> assistantStream(Long registerId, String message) {
        return Map.of(
                "stub", true,
                "registerId", registerId,
                "delta", "【占位】AI 助理未启用，请依据病历人工判断。"
        );
    }

    public Map<String, Object> diagnosisSuggest(Long registerId, String symptomsSummary) {
        return Map.of(
                "stub", true,
                "registerId", registerId,
                "suggestions", List.of(),
                "needCheck", true,
                "needInspection", false,
                "reason", "AI module disabled"
        );
    }
}
