package com.hospital.aibridge.service;

import com.hospital.aibridge.config.RagProperties;
import com.hospital.aibridge.repository.RagKnowledgeRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * 第一版联调用的最小知识库。
 *
 * <p>内容仅演示 RAG 链路，sourceVersion 固定为 DEMO-1.0。上线前必须由医院
 * 用经过审核、带版本和生效日期的正式指南替换，不能作为独立诊疗依据。</p>
 */
@Component
public class DemoKnowledgeInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoKnowledgeInitializer.class);

    private final RagProperties properties;
    private final RagKnowledgeRepository repository;
    private final EmbeddingModel embeddingModel;

    public DemoKnowledgeInitializer(
            RagProperties properties,
            RagKnowledgeRepository repository,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.properties = properties;
        this.repository = repository;
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
    }

    @PostConstruct
    void seed() {
        if (!properties.isEnabled() || embeddingModel == null || !repository.isAvailable()
                || repository.countDocuments() > 0) {
            return;
        }
        for (DemoDocument document : loadDocuments()) {
            try {
                repository.insertDemoDocument(
                        document.code(), document.title(), document.knowledgeType(), document.content(),
                        embeddingModel.embed(document.content()),
                        Map.of("dataLevel", "DEMO", "reviewRequired", true, "scene", document.knowledgeType()));
            } catch (Exception ex) {
                // 单个文档失败不影响其余文档和门诊启动。
                log.warn("Demo knowledge embedding failed for {}: {}", document.code(), ex.getMessage());
            }
        }
    }

    private DemoDocument[] loadDocuments() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:rag/demo/*/*.txt");
            return Arrays.stream(resources).map(this::readDocument).toArray(DemoDocument[]::new);
        } catch (Exception ex) {
            log.warn("Failed to load TXT demo knowledge: {}", ex.getMessage());
            return new DemoDocument[0];
        }
    }

    private DemoDocument readDocument(Resource resource) {
        try {
            String path = resource.getURL().toString().replace('\\', '/');
            String type = path.substring(path.lastIndexOf('/', path.lastIndexOf('/') - 1) + 1, path.lastIndexOf('/'));
            String filename = resource.getFilename();
            String code = filename == null ? "DEMO-UNKNOWN" : filename.replaceFirst("\\.txt$", "");
            String raw = resource.getContentAsString(StandardCharsets.UTF_8).trim();
            String[] lines = raw.split("\\R", 2);
            String title = lines[0].replaceFirst("^#\\s*", "").trim();
            String content = lines.length > 1 ? lines[1].trim() : title;
            return new DemoDocument(code, title, type, content);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid TXT knowledge resource: " + resource, ex);
        }
    }

    private record DemoDocument(String code, String title, String knowledgeType, String content) {
    }
}
