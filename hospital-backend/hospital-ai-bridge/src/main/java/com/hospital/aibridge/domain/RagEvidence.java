package com.hospital.aibridge.domain;

import java.util.Map;

/**
 * 一条可展示、可审计的 RAG 检索证据。
 */
public record RagEvidence(
        Long chunkId,
        Long documentId,
        String sourceName,
        String sourceVersion,
        String title,
        String content,
        double score,
        Map<String, Object> metadata) {
}
