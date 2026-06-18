package com.hospital.aibridge.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.RagProperties;
import com.hospital.aibridge.domain.RagEvidence;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * pgvector 知识库访问层。
 *
 * <p>本仓储只负责非结构化医学知识；药品和医技目录继续由 AiCatalogRepository
 * 使用普通 SQL 查询，以保证项目 ID、库存和启停状态的确定性。</p>
 */
@Repository
public class RagKnowledgeRepository {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeRepository.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private volatile boolean available;

    public RagKnowledgeRepository(JdbcClient jdbcClient, ObjectMapper objectMapper, RagProperties properties) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void initialize() {
        if (!properties.isEnabled() || !properties.isInitializeSchema()) {
            available = schemaExists();
            return;
        }
        try {
            jdbcClient.sql("CREATE EXTENSION IF NOT EXISTS vector").update();
            jdbcClient.sql("""
                    CREATE TABLE IF NOT EXISTS ai_knowledge_document (
                        id BIGSERIAL PRIMARY KEY,
                        document_code VARCHAR(128) NOT NULL UNIQUE,
                        title VARCHAR(255) NOT NULL,
                        knowledge_type VARCHAR(64) NOT NULL,
                        source_name VARCHAR(255),
                        source_version VARCHAR(64),
                        effective_date DATE,
                        expire_date DATE,
                        status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                        create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """).update();
            // pgvector 的列维度必须与配置的 DashScope Embedding 输出维度保持一致。
            jdbcClient.sql("""
                    CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
                        id BIGSERIAL PRIMARY KEY,
                        document_id BIGINT NOT NULL REFERENCES ai_knowledge_document(id) ON DELETE CASCADE,
                        chunk_no INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        token_count INTEGER,
                        embedding vector(%d) NOT NULL,
                        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
                        create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(document_id, chunk_no)
                    )
                    """.formatted(properties.getDimensions())).update();
            jdbcClient.sql("CREATE INDEX IF NOT EXISTS ix_ai_knowledge_document_type_status ON ai_knowledge_document(knowledge_type, status)").update();
            jdbcClient.sql("CREATE INDEX IF NOT EXISTS ix_ai_knowledge_chunk_embedding ON ai_knowledge_chunk USING hnsw (embedding vector_cosine_ops)").update();
            available = true;
        } catch (Exception ex) {
            // RAG 故障不能阻断门诊主流程，调用方会降级到原有目录候选逻辑。
            available = false;
            log.warn("RAG schema initialization failed; doctor AI will use safe fallback: {}", ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public long countDocuments() {
        if (!available) {
            return 0L;
        }
        try {
            Long count = jdbcClient.sql("SELECT COUNT(*) FROM ai_knowledge_document")
                    .query(Long.class).single();
            return count == null ? 0L : count;
        } catch (Exception ex) {
            return 0L;
        }
    }

    /**
     * 保存一个单切片演示文档。正式知识导入可沿用此表结构扩展为多切片。
     */
    public void insertDemoDocument(String code, String title, String knowledgeType,
                                   String content, float[] embedding, Map<String, Object> metadata) {
        if (!available || embedding == null || embedding.length != properties.getDimensions()) {
            return;
        }
        try {
            String metadataJson = objectMapper.writeValueAsString(metadata);
            Long documentId = jdbcClient.sql("""
                            INSERT INTO ai_knowledge_document
                                (document_code, title, knowledge_type, source_name, source_version,
                                 effective_date, status, metadata)
                            VALUES (:code, :title, :knowledgeType, '内置演示知识库', 'DEMO-1.0',
                                    :effectiveDate, 'ACTIVE', CAST(:metadata AS jsonb))
                            ON CONFLICT (document_code) DO UPDATE SET
                                title = EXCLUDED.title,
                                knowledge_type = EXCLUDED.knowledge_type,
                                metadata = EXCLUDED.metadata,
                                update_time = CURRENT_TIMESTAMP
                            RETURNING id
                            """)
                    .param("code", code)
                    .param("title", title)
                    .param("knowledgeType", knowledgeType)
                    .param("effectiveDate", LocalDate.now())
                    .param("metadata", metadataJson)
                    .query(Long.class).single();
            jdbcClient.sql("""
                            INSERT INTO ai_knowledge_chunk
                                (document_id, chunk_no, content, token_count, embedding, metadata)
                            VALUES (:documentId, 1, :content, :tokenCount,
                                    CAST(:embedding AS vector), CAST(:metadata AS jsonb))
                            ON CONFLICT (document_id, chunk_no) DO UPDATE SET
                                content = EXCLUDED.content,
                                token_count = EXCLUDED.token_count,
                                embedding = EXCLUDED.embedding,
                                metadata = EXCLUDED.metadata
                            """)
                    .param("documentId", documentId)
                    .param("content", content)
                    .param("tokenCount", Math.max(1, content.length() / 2))
                    .param("embedding", vectorLiteral(embedding))
                    .param("metadata", metadataJson)
                    .update();
        } catch (Exception ex) {
            log.warn("Failed to insert demo RAG document {}: {}", code, ex.getMessage());
        }
    }

    public List<RagEvidence> search(String knowledgeType, float[] embedding, int topK, double threshold) {
        if (!available || embedding == null || embedding.length != properties.getDimensions()) {
            return List.of();
        }
        try {
            return jdbcClient.sql("""
                            SELECT c.id AS chunk_id, d.id AS document_id, d.source_name,
                                   d.source_version, d.title, c.content, c.metadata::text,
                                   1 - (c.embedding <=> CAST(:embedding AS vector)) AS score
                            FROM ai_knowledge_chunk c
                            JOIN ai_knowledge_document d ON d.id = c.document_id
                            WHERE d.knowledge_type = :knowledgeType
                              AND d.status = 'ACTIVE'
                              AND (d.effective_date IS NULL OR d.effective_date <= CURRENT_DATE)
                              AND (d.expire_date IS NULL OR d.expire_date >= CURRENT_DATE)
                              AND 1 - (c.embedding <=> CAST(:embedding AS vector)) >= :threshold
                            ORDER BY c.embedding <=> CAST(:embedding AS vector)
                            LIMIT :topK
                            """)
                    .param("embedding", vectorLiteral(embedding))
                    .param("knowledgeType", knowledgeType)
                    .param("threshold", threshold)
                    .param("topK", topK)
                    .query((rs, rowNum) -> new RagEvidence(
                            rs.getLong("chunk_id"),
                            rs.getLong("document_id"),
                            rs.getString("source_name"),
                            rs.getString("source_version"),
                            rs.getString("title"),
                            rs.getString("content"),
                            rs.getDouble("score"),
                            readMetadata(rs.getString("metadata"))))
                    .list();
        } catch (Exception ex) {
            log.warn("RAG vector search failed and was degraded: {}", ex.getMessage());
            return List.of();
        }
    }

    private boolean schemaExists() {
        try {
            Boolean exists = jdbcClient.sql("SELECT to_regclass('public.ai_knowledge_chunk') IS NOT NULL")
                    .query(Boolean.class).single();
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, Object> readMetadata(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception ex) {
            throw new SQLException("Invalid RAG metadata JSON", ex);
        }
    }

    private String vectorLiteral(float[] values) {
        StringBuilder builder = new StringBuilder(values.length * 8).append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.append(']').toString();
    }
}
