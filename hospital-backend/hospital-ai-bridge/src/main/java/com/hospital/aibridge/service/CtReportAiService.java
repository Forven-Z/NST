package com.hospital.aibridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.dto.HeadCtImpressionRequest;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CtReportAiService {

    private static final String SYSTEM_PROMPT = """
            You are a Chinese radiology report assistant for imaging reports.
            Generate the diagnostic impression for the specific imaging exam from the physician-provided findings/report text.
            The exam may be head CT, lung/chest CT, tumor CT, tumor segmentation, or another imaging exam.
            Use the exam item name, modality, and body part to choose the appropriate wording.
            Only summarize information supported by the provided findings/report text.
            Do not invent lesions, diagnoses, measurements, urgency, staging, segmentation volume, or recommendations not supported by the findings.
            Do not output Markdown.
            Do not include a section title such as "诊断印象".
            Output exactly one JSON object: {"impression":"中文诊断印象正文"}.
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public CtReportAiService(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
    }

    public Map<String, Object> generateHeadCtImpression(HeadCtImpressionRequest request) {
        return generateImagingImpression(request);
    }

    public Map<String, Object> generateImagingImpression(HeadCtImpressionRequest request) {
        String findings = request != null ? trim(request.getFindingsText()) : "";
        if (!StringUtils.hasText(findings)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先填写检查所见");
        }
        if (!aiProperties.isEnabled() || chatClient == null) {
            throw new BusinessException(ErrorCode.AI_DISABLED, "AI 报告生成服务未启用或未配置");
        }

        String content;
        try {
            content = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserPrompt(request, findings))
                    .call()
                    .content();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "调用 LLM 生成诊断印象失败: " + ex.getMessage());
        }

        String impression = parseImpression(content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aiReportText", impression);
        result.put("aiReportStatus", "READY");
        return result;
    }

    private String buildUserPrompt(HeadCtImpressionRequest request, String findings) {
        return """
                检查项目：%s
                影像类型：%s
                检查部位：%s
                患者性别：%s
                患者年龄：%s
                临床诊断：%s

                医师填写的检查所见/报告内容：
                %s

                请仅基于上述内容生成与检查项目匹配的诊断印象。
                """.formatted(
                value(request.getItemName()),
                value(request.getModality()),
                value(request.getBodyPart()),
                value(request.getPatientGender()),
                value(request.getPatientAge()),
                value(request.getClinicalDiagnosis()),
                findings
        );
    }

    String parseImpression(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 未返回诊断印象");
        }
        try {
            Map<String, Object> json = objectMapper.readValue(cleanJson(content), new TypeReference<>() {
            });
            String impression = trim(json.get("impression"));
            impression = normalizeImpression(impression);
            if (!StringUtils.hasText(impression)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 返回的诊断印象为空");
            }
            return impression;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "LLM 返回格式无效，未生成诊断印象");
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

    private String normalizeImpression(String impression) {
        return impression
                .replaceFirst("^【?诊断印象】?\\s*[:：]?\\s*", "")
                .replaceFirst("^印象\\s*[:：]?\\s*", "")
                .trim();
    }

    private String value(String text) {
        return StringUtils.hasText(text) ? text.trim() : "未提供";
    }

    private String trim(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
