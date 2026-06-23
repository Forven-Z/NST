package com.hospital.aibridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import com.hospital.aibridge.domain.RagEvidence;
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
    private final MedicalKnowledgeRetriever knowledgeRetriever;
    private final DraftSafetyValidator safetyValidator;

    public DoctorAiAssistService(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            MedicalKnowledgeRetriever knowledgeRetriever,
            DraftSafetyValidator safetyValidator,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.knowledgeRetriever = knowledgeRetriever;
        this.safetyValidator = safetyValidator;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
    }

    public Map<String, Object> diagnosisSuggest(DiagnosisSuggestRequest request) {
        Map<String, Object> fallback = fallbackDiagnosis(request);
        MedicalKnowledgeRetriever.RetrievalResult retrieval = knowledgeRetriever.retrieveDiagnosis(request);
        if (!enabled()) {
            attachRetrieval(fallback, retrieval);
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

                检索到的参考知识：
                %s
                只能将参考知识作为辅助依据；若知识不足或与患者情况不符，应明确提示医生进一步判断。
                """.formatted(recordText(request), evidenceText(retrieval.evidence()));
        Map<String, Object> result = callJson(prompt).orElse(fallback);
        attachRetrieval(result, retrieval);
        result.put("safetyNotice", SAFETY_NOTICE);
        return result;
    }

    public Map<String, Object> generateDraft(DoctorAiDraftRequest request) {
        Map<String, Object> fallback = fallbackDraft(request);
        MedicalKnowledgeRetriever.RetrievalResult retrieval = knowledgeRetriever.retrieveDraft(
                request.getDraftType(), request.getMedicalRecord());
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

                检索到的参考知识：
                %s
                参考知识用于判断医学必要性，但最终只能返回候选项目中真实存在的 ID。
                """.formatted(request.getDraftType(), recordText(request.getMedicalRecord()),
                writeJson(request.getCandidates()), evidenceText(retrieval.evidence()));
        prompt = prompt + "\n\n检查/检验返回结果：\n" + resultContextText(request.getClinicalResultContext())
                + "\n\n处方/处置生成约束：若本次生成的是 PRESCRIPTION 或 DISPOSAL，必须结合检查/检验返回结果，"
                + "不得基于未返回结果进行推断；如果结果与候选处方/处置不匹配，应保守处理并在 aiReason 中提示医生核对。";
        Map<String, Object> result = enabled() ? callJson(prompt).orElse(fallback) : fallback;
        DraftSafetyValidator.ValidationResult validation = safetyValidator.validate(
                request.getDraftType(), resultItems(result), request.getCandidates());
        result.put("items", validation.items());
        result.put("warnings", validation.warnings());
        result.put("safetyNotice", SAFETY_NOTICE);
        attachRetrieval(result, retrieval);
        return result;
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

    private String resultContextText(String value) {
        return StringUtils.hasText(value) ? value : "无已返回检查/检验结果";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> resultItems(Map<String, Object> result) {
        Object value = result.get("items");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) new LinkedHashMap<>((Map<String, Object>) item))
                .toList();
    }

    private void attachRetrieval(Map<String, Object> result,
                                 MedicalKnowledgeRetriever.RetrievalResult retrieval) {
        result.put("ragEnabled", retrieval.ragEnabled());
        result.put("evidence", retrieval.evidence().stream().map(this::evidenceMap).toList());
    }

    private Map<String, Object> evidenceMap(RagEvidence evidence) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("chunkId", evidence.chunkId());
        item.put("documentId", evidence.documentId());
        item.put("sourceName", evidence.sourceName());
        item.put("sourceVersion", evidence.sourceVersion());
        item.put("title", evidence.title());
        item.put("excerpt", evidence.content());
        item.put("score", Math.round(evidence.score() * 1000D) / 1000D);
        item.put("sourceUrl", evidence.metadata().get("sourceUrl"));
        item.put("effectiveDate", evidence.metadata().get("effectiveDate"));
        item.put("reviewRequired", evidence.metadata().getOrDefault("reviewRequired", true));
        return item;
    }

    private String evidenceText(List<RagEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "未检索到适用知识；不得臆造指南依据。";
        }
        return evidence.stream()
                .map(item -> "[%s / %s / 相似度 %.3f] %s".formatted(
                        item.title(), item.sourceVersion(), item.score(), item.content()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("未检索到适用知识；不得臆造指南依据。");
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
