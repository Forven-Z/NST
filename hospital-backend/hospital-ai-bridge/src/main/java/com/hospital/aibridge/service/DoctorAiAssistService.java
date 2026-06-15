package com.hospital.aibridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.dto.DiagnosisSuggestRequest;
import com.hospital.aibridge.dto.DoctorAiDraftRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DoctorAiAssistService {

    private static final String SAFETY_NOTICE = "本结果仅作为医生辅助参考，不能替代医生最终诊断、处方或医嘱。";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public DoctorAiAssistService(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
    }

    public Map<String, Object> diagnosisSuggest(DiagnosisSuggestRequest request) {
        Map<String, Object> fallback = fallbackDiagnosis(request);
        if (!enabled()) {
            return fallback;
        }
        String prompt = """
                你是智慧云脑诊疗平台的医生端 AI 辅助诊疗助手。
                你只能提供初步诊断和下一步检查/检验/处置建议，不能替代医生确诊。
                请基于门诊病历内容输出严格 JSON，不要 Markdown。
                JSON 字段固定为：
                {
                  "suggestions": ["可能诊断或鉴别诊断建议"],
                  "needCheck": true,
                  "needInspection": true,
                  "needDisposal": false,
                  "reason": "给医生看的简短依据和风险提示"
                }

                病历内容：
                %s
                """.formatted(recordText(request));
        return callJson(prompt).orElse(fallback);
    }

    public Map<String, Object> generateDraft(DoctorAiDraftRequest request) {
        Map<String, Object> fallback = fallbackDraft(request);
        if (!enabled()) {
            return fallback;
        }
        String prompt = """
                你是智慧云脑诊疗平台的医生端 AI 辅助开单助手。
                请为医生生成 %s 草稿。只能从候选项目中选择，必须保留 medicalTechnologyId 或 drugId。
                输出严格 JSON，不要 Markdown。AI 输出仅供医生编辑确认。
                JSON 字段固定为：
                {
                  "aiReason": "生成草稿的医学依据和需医生核对的注意点",
                  "items": [
                    {
                      "medicalTechnologyId": 1,
                      "drugId": 1,
                      "itemName": "项目名",
                      "drugName": "药品名",
                      "purpose": "目的",
                      "bodyPart": "部位",
                      "remark": "备注",
                      "quantity": 1,
                      "usageMethod": "用法",
                      "dosage": "剂量",
                      "frequency": "频次",
                      "days": 3,
                      "entrust": "嘱托"
                    }
                  ]
                }

                病历内容：
                %s

                候选项目：
                %s
                """.formatted(request.getDraftType(), recordText(request.getMedicalRecord()), writeJson(request.getCandidates()));
        return callJson(prompt).orElse(fallback);
    }

    private boolean enabled() {
        return aiProperties.isEnabled() && chatClient != null;
    }

    private Optional<Map<String, Object>> callJson(String prompt) {
        try {
            String content = chatClient.prompt()
                    .system("你必须使用中文回答，并且只输出一个 JSON 对象。")
                    .user(prompt)
                    .call()
                    .content();
            return Optional.of(objectMapper.readValue(cleanJson(content), new TypeReference<>() {
            }));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Map<String, Object> fallbackDiagnosis(DiagnosisSuggestRequest request) {
        String reason = StringUtils.hasText(request.getDiagnosis())
                ? "已结合医生当前诊断和病历内容生成辅助建议。"
                : "请结合主诉、现病史、体格检查完善鉴别诊断。";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stub", true);
        result.put("registerId", request.getRegisterId());
        result.put("suggestions", List.of(
                "结合主诉、现病史和体格检查完善初步诊断",
                "必要时完善相关检查/检验以支持鉴别诊断"
        ));
        result.put("needCheck", true);
        result.put("needInspection", true);
        result.put("needDisposal", false);
        result.put("reason", reason + SAFETY_NOTICE);
        return result;
    }

    private Map<String, Object> fallbackDraft(DoctorAiDraftRequest request) {
        List<Map<String, Object>> candidates = request.getCandidates() == null ? List.of() : request.getCandidates();
        List<Map<String, Object>> items = candidates.stream().limit(1).map(this::normalizeCandidate).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stub", true);
        result.put("aiReason", "AI 服务不可用，已按候选目录生成最小草稿，请医生核对后再确认。");
        result.put("items", items);
        return result;
    }

    private Map<String, Object> normalizeCandidate(Map<String, Object> candidate) {
        Map<String, Object> item = new LinkedHashMap<>(candidate);
        if (item.containsKey("id") && !item.containsKey("medicalTechnologyId") && !item.containsKey("drugId")) {
            item.put("medicalTechnologyId", item.get("id"));
        }
        item.putIfAbsent("purpose", "辅助明确诊断");
        item.putIfAbsent("bodyPart", "");
        item.putIfAbsent("remark", "");
        item.putIfAbsent("quantity", 1);
        item.putIfAbsent("usageMethod", "口服");
        item.putIfAbsent("dosage", "");
        item.putIfAbsent("frequency", "");
        item.putIfAbsent("days", 3);
        item.putIfAbsent("entrust", "遵医嘱");
        return item;
    }

    private String recordText(DiagnosisSuggestRequest request) {
        if (request == null) {
            return "无";
        }
        if (StringUtils.hasText(request.getSymptomsSummary())) {
            return request.getSymptomsSummary();
        }
        return """
                主诉：%s
                现病史：%s
                现病治疗情况：%s
                既往史/个人史：%s
                过敏史：%s
                体格检查：%s
                初步诊断：%s
                处置建议：%s
                检查建议：%s
                检验建议：%s
                """.formatted(
                text(request.getReadme()),
                text(request.getPresent()),
                text(request.getPresentTreat()),
                text(request.getHistory()),
                text(request.getAllergy()),
                text(request.getPhysique()),
                text(request.getDiagnosis()),
                text(request.getCure()),
                text(request.getCheckAdvice()),
                text(request.getInspectionAdvice())
        );
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value : "未填写";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String cleanJson(String content) {
        if (content == null) {
            return "{}";
        }
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
}
