package com.hospital.aibridge.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class AiPrescriptionDraftRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public AiPrescriptionDraftRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public long insert(Long registerId, Long doctorId, Map<String, Object> draftContent) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO ai_prescription_draft (register_id, doctor_id, draft_content, status)
                        VALUES (:registerId, :doctorId, CAST(:draftContent AS jsonb), 0)
                        """)
                .param("registerId", registerId)
                .param("doctorId", doctorId)
                .param("draftContent", writeJson(draftContent))
                .update(keyHolder, "id");
        return keyHolder.getKey().longValue();
    }

    public void updateEditedContent(Long draftId, Map<String, Object> editedContent) {
        jdbcClient.sql("""
                        UPDATE ai_prescription_draft
                        SET doctor_edited_content = CAST(:editedContent AS jsonb),
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", draftId)
                .param("editedContent", writeJson(editedContent))
                .update();
    }

    public void markSubmitted(Long draftId, Map<String, Object> finalContent) {
        jdbcClient.sql("""
                        UPDATE ai_prescription_draft
                        SET doctor_edited_content = CAST(:finalContent AS jsonb),
                            status = 1,
                            submit_time = NOW(),
                            update_time = NOW()
                        WHERE id = :id
                        """)
                .param("id", draftId)
                .param("finalContent", writeJson(finalContent))
                .update();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
