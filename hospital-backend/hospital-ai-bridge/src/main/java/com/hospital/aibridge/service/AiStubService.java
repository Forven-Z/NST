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
                "delta", "您好，请先完成挂号后再咨询导诊问题。"
        );
    }

    public Map<String, Object> assistantStream(Long registerId, String message) {
        return Map.of(
                "stub", true,
                "registerId", registerId,
                "delta", "AI 助理暂未启用，请依据病历与诊疗规范人工判断。"
        );
    }

    public Map<String, Object> diagnosisSuggest(Long registerId, String symptomsSummary) {
        return Map.of(
                "stub", true,
                "registerId", registerId,
                "suggestions", List.of(),
                "needCheck", true,
                "needInspection", false,
                "reason", "AI 服务暂未启用"
        );
    }
}
