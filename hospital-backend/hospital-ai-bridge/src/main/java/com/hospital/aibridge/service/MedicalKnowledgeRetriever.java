package com.hospital.aibridge.service;

import com.hospital.aibridge.config.RagProperties;
import com.hospital.aibridge.domain.RagEvidence;
import com.hospital.aibridge.dto.DiagnosisSuggestRequest;
import com.hospital.aibridge.repository.RagKnowledgeRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 使用 Spring AI 的 EmbeddingModel（由 DashScope OpenAI 兼容接口提供）生成查询向量。
 */
@Service
public class MedicalKnowledgeRetriever {

    private final RagProperties properties;
    private final RagKnowledgeRepository repository;
    private final EmbeddingModel embeddingModel;

    public MedicalKnowledgeRetriever(
            RagProperties properties,
            RagKnowledgeRepository repository,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.properties = properties;
        this.repository = repository;
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
    }

    public RetrievalResult retrieveDiagnosis(DiagnosisSuggestRequest record) {
        return retrieve("CLINICAL_GUIDELINE", clinicalQuery(record));
    }

    public RetrievalResult retrieveDraft(String draftType, DiagnosisSuggestRequest record) {
        String knowledgeType = switch (draftType) {
            case "PRESCRIPTION" -> "DRUG_INSTRUCTION";
            case "DISPOSAL" -> "DISPOSAL_GUIDE";
            case "CHECK", "INSPECTION" -> "TECHNOLOGY_GUIDE";
            default -> "CLINICAL_GUIDELINE";
        };
        return retrieve(knowledgeType, clinicalQuery(record));
    }

    private RetrievalResult retrieve(String knowledgeType, String query) {
        if (!properties.isEnabled() || !repository.isAvailable() || embeddingModel == null || !StringUtils.hasText(query)) {
            return RetrievalResult.disabled();
        }
        try {
            float[] embedding = embeddingModel.embed(query);
            List<RagEvidence> evidence = repository.search(
                    knowledgeType, embedding, properties.getTopK(), properties.getSimilarityThreshold());
            return new RetrievalResult(true, evidence);
        } catch (Exception ex) {
            // Embedding 或检索异常均采用无 RAG 上下文降级，不能影响医生正常开单。
            return RetrievalResult.disabled();
        }
    }

    private String clinicalQuery(DiagnosisSuggestRequest record) {
        if (record == null) {
            return "";
        }
        return Stream.of(
                value("症状摘要", record.getSymptomsSummary()),
                value("主诉", record.getReadme()),
                value("现病史", record.getPresent()),
                value("既往史", record.getHistory()),
                value("过敏史", record.getAllergy()),
                value("体格检查", record.getPhysique()),
                value("初步诊断", record.getDiagnosis()))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("；"));
    }

    private String value(String label, String value) {
        return StringUtils.hasText(value) ? label + "：" + value : "";
    }

    public record RetrievalResult(boolean ragEnabled, List<RagEvidence> evidence) {
        public static RetrievalResult disabled() {
            return new RetrievalResult(false, List.of());
        }
    }
}
