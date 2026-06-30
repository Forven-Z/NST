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
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 将随应用发布的 UTF-8 TXT 医疗摘要增量同步到 pgvector。
 */
@Component
public class DemoKnowledgeInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoKnowledgeInitializer.class);
    private static final int EXPECTED_DOCUMENT_COUNT = 100;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "CLINICAL_GUIDELINE", "TECHNOLOGY_GUIDE", "DRUG_INSTRUCTION", "DISPOSAL_GUIDE");

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
        if (!properties.isEnabled() || embeddingModel == null || !repository.isAvailable()) {
            return;
        }
        KnowledgeDocument[] documents = loadDocuments();
        if (!validateDocuments(documents)) {
            return;
        }
        int inserted = 0;
        int skipped = 0;
        int failed = 0;
        for (KnowledgeDocument document : documents) {
            try {
                String contentHash = sha256(canonicalContent(document));
                if (repository.findContentHash(document.code()).filter(contentHash::equals).isPresent()) {
                    skipped++;
                    continue;
                }
                Map<String, Object> metadata = new LinkedHashMap<>(document.metadata());
                metadata.put("contentHash", contentHash);
                metadata.put("dataLevel", "OFFICIAL_SUMMARY");
                metadata.put("reviewRequired", true);
                metadata.put("scene", document.knowledgeType());
                List<RagKnowledgeRepository.KnowledgeChunk> chunks = new ArrayList<>();
                int chunkNo = 1;
                for (String content : split(document.content())) {
                    String embeddingText = embeddingText(document, content);
                    chunks.add(new RagKnowledgeRepository.KnowledgeChunk(
                            chunkNo++, content, embeddingModel.embed(embeddingText)));
                }
                repository.upsertDocument(document.code(), document.title(), document.knowledgeType(),
                        document.sourceName(), document.sourceVersion(), document.effectiveDate(), metadata, chunks);
                inserted++;
            } catch (Exception ex) {
                failed++;
                log.warn("Official knowledge sync failed for {}: {}", document.code(), ex.getMessage());
            }
        }
        int deactivated = 0;
        if (failed == 0 && repository.countActiveOfficialDocuments() == EXPECTED_DOCUMENT_COUNT) {
            deactivated = repository.deactivateDemoDocuments();
        } else {
            log.warn("Official knowledge is incomplete; demo documents remain active: activeOfficial={}, expected={}",
                    repository.countActiveOfficialDocuments(), EXPECTED_DOCUMENT_COUNT);
        }
        log.info("Official knowledge sync finished: updated={}, skipped={}, failed={}, demoDeactivated={}",
                inserted, skipped, failed, deactivated);
    }

    private KnowledgeDocument[] loadDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = {
                    resolver.getResource("classpath:rag/official/CLINICAL_GUIDELINE/clinical-guidelines.txt"),
                    resolver.getResource("classpath:rag/official/TECHNOLOGY_GUIDE/technology-guides.txt"),
                    resolver.getResource("classpath:rag/official/DRUG_INSTRUCTION/drug-instructions.txt"),
                    resolver.getResource("classpath:rag/official/DISPOSAL_GUIDE/disposal-guides.txt")
            };
            return Arrays.stream(resources)
                    .flatMap(resource -> readDocuments(resource).stream())
                    .toArray(KnowledgeDocument[]::new);
        } catch (Exception ex) {
            log.warn("Failed to load official TXT knowledge: {}", ex.getMessage());
            return new KnowledgeDocument[0];
        }
    }

    private boolean validateDocuments(KnowledgeDocument[] documents) {
        if (documents.length != EXPECTED_DOCUMENT_COUNT) {
            log.warn("Official knowledge count mismatch: actual={}, expected={}",
                    documents.length, EXPECTED_DOCUMENT_COUNT);
            return false;
        }
        Set<String> codes = new HashSet<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (KnowledgeDocument document : documents) {
            if (!codes.add(document.code())) {
                log.warn("Duplicate official knowledge code: {}", document.code());
                return false;
            }
            if (!SUPPORTED_TYPES.contains(document.knowledgeType()) || document.content().isBlank()) {
                log.warn("Invalid official knowledge document: {}", document.code());
                return false;
            }
            counts.merge(document.knowledgeType(), 1, Integer::sum);
        }
        Map<String, Integer> expected = Map.of(
                "CLINICAL_GUIDELINE", 35,
                "TECHNOLOGY_GUIDE", 25,
                "DRUG_INSTRUCTION", 25,
                "DISPOSAL_GUIDE", 15);
        if (!expected.equals(counts)) {
            log.warn("Official knowledge category count mismatch: actual={}, expected={}", counts, expected);
            return false;
        }
        return true;
    }

    private List<KnowledgeDocument> readDocuments(Resource resource) {
        try {
            String raw = resource.getContentAsString(StandardCharsets.UTF_8).trim();
            return Stream.of(raw.split("(?m)^=== DOCUMENT ===\\s*$"))
                    .map(String::trim)
                    .filter(section -> !section.isBlank())
                    .map(section -> readDocument(resource, section))
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid TXT knowledge resource: " + resource, ex);
        }
    }

    private KnowledgeDocument readDocument(Resource resource, String raw) {
        try {
            String[] parts = raw.split("\\R---\\R", 2);
            if (!raw.startsWith("---") || parts.length != 2) {
                throw new IllegalArgumentException("missing metadata header");
            }
            Map<String, String> header = new LinkedHashMap<>();
            Arrays.stream(parts[0].replaceFirst("^---\\R", "").split("\\R"))
                    .filter(line -> line.contains(":"))
                    .forEach(line -> header.put(line.substring(0, line.indexOf(':')).trim(),
                            line.substring(line.indexOf(':') + 1).trim()));
            String content = parts[1].trim();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceUrl", required(header, "sourceUrl"));
            metadata.put("accessedAt", required(header, "accessedAt"));
            metadata.put("departments", csv(header.get("departments")));
            metadata.put("topics", csv(header.get("topics")));
            metadata.put("effectiveDate", header.getOrDefault("effectiveDate", ""));
            return new KnowledgeDocument(required(header, "documentCode"), required(header, "title"),
                    required(header, "knowledgeType"), required(header, "sourceName"),
                    required(header, "sourceVersion"), date(header.get("effectiveDate")), content, metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid TXT knowledge resource: " + resource, ex);
        }
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }

    private List<String> csv(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(","))
                .map(String::trim).filter(it -> !it.isBlank()).toList();
    }

    private LocalDate date(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String canonicalContent(KnowledgeDocument document) {
        return document.title() + "\n" + document.knowledgeType() + "\n"
                + document.metadata().getOrDefault("topics", List.of()) + "\n" + document.content();
    }

    private String embeddingText(KnowledgeDocument document, String content) {
        return "标题：" + document.title() + "\n主题："
                + document.metadata().getOrDefault("topics", List.of()) + "\n正文：" + content;
    }

    private List<String> split(String content) {
        if (content.length() <= 800) {
            return List.of(content.trim());
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\\R\\s*\\R")) {
            if (paragraph.length() > 800) {
                for (int start = 0; start < paragraph.length(); start += 800) {
                    if (!current.isEmpty()) {
                        chunks.add(current.toString().trim());
                        current.setLength(0);
                    }
                    chunks.add(paragraph.substring(start, Math.min(start + 800, paragraph.length())).trim());
                }
                continue;
            }
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > 800) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(paragraph.trim()).append("\n\n");
        }
        if (!current.isEmpty()) chunks.add(current.toString().trim());
        return chunks;
    }

    private record KnowledgeDocument(String code, String title, String knowledgeType,
                                     String sourceName, String sourceVersion, LocalDate effectiveDate,
                                     String content, Map<String, Object> metadata) { }
}
