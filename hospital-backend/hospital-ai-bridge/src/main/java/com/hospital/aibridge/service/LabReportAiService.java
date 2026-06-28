package com.hospital.aibridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.dto.LabAnalysisRequest;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LabReportAiService {

    private static final String SYSTEM_PROMPT = """
            You are a Chinese laboratory report assistant.
            Generate a concise diagnostic analysis from the provided lab result table only.
            Focus on abnormal items and summarize normal items briefly when useful.
            Do not make a definitive diagnosis, prescribe drugs, or provide treatment plans.
            Do not invent lab items, values, diseases, or clinical facts not present in the input.
            Do not output Markdown.
            Do not include a section title such as "诊断分析".
            Output exactly one JSON object: {"analysis":"中文诊断分析正文"}.
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public LabReportAiService(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
    }

    public Map<String, Object> generateLabAnalysis(LabAnalysisRequest request) {
        List<Map<String, Object>> items = request != null && request.getItems() != null
                ? request.getItems() : List.of();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检验项目不能为空");
        }
        if (!aiProperties.isEnabled() || chatClient == null) {
            throw new BusinessException(ErrorCode.AI_DISABLED, "AI 检验报告生成服务未启用或未配置");
        }

        String content;
        try {
            content = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request, items))
                    .call()
                    .content();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "调用 LLM 生成检验诊断分析失败: " + ex.getMessage());
        }

        String analysis = parseAnalysis(content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiReportText", analysis);
        result.put("aiReportStatus", "READY");
        return result;
    }

    private String buildUserPrompt(LabAnalysisRequest request, List<Map<String, Object>> items) {
        return """
                检验项目：%s
                患者性别：%s
                患者年龄：%s
                临床诊断：%s

                检验数据：
                %s

                请仅基于上述检验数据生成诊断分析。
                """.formatted(
                value(request.getItemName()),
                value(request.getPatientGender()),
                value(request.getPatientAge()),
                value(request.getClinicalDiagnosis()),
                writeJson(items)
        );
    }

    String parseAnalysis(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 未返回检验诊断分析");
        }
        try {
            Map<String, Object> json = objectMapper.readValue(cleanJson(content), new TypeReference<>() {
            });
            String analysis = trim(json.get("analysis"));
            analysis = normalizeAnalysis(analysis);
            if (!StringUtils.hasText(analysis)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 返回的检验诊断分析为空");
            }
            return analysis;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 返回格式无效，未生成检验诊断分析");
        }
    }

    private String cleanJson(String content) {
        String value = content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private String normalizeAnalysis(String analysis) {
        return analysis
                .replaceFirst("^【?诊断分析】?\\s*[:：]?\\s*", "")
                .replaceFirst("^分析\\s*[:：]?\\s*", "")
                .trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String value(String text) {
        return StringUtils.hasText(text) ? text.trim() : "未提供";
    }

    private String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
