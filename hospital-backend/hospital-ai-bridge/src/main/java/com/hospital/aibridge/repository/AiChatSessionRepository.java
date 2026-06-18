package com.hospital.aibridge.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiChatSessionRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public AiChatSessionRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public String insertSession(String scene, Long registerId, Long patientId, Long doctorId,
                                List<Map<String, Object>> messages) {
        return insertSessionPayload(scene, registerId, patientId, doctorId, messages);
    }

    public String insertSessionPayload(String scene, Long registerId, Long patientId, Long doctorId,
                                       Object messages) {
        String sessionNo = "AI" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        jdbcClient.sql("""
                        INSERT INTO ai_chat_session
                            (session_no, scene, patient_id, register_id, doctor_id, messages)
                        VALUES
                            (:sessionNo, :scene, :patientId, :registerId, :doctorId, CAST(:messages AS jsonb))
                        """)
                .param("sessionNo", sessionNo)
                .param("scene", scene)
                .param("patientId", patientId)
                .param("registerId", registerId)
                .param("doctorId", doctorId)
                .param("messages", writeJson(messages))
                .update();
        return sessionNo;
    }

    public Optional<StoredSession> findActiveBySessionNo(String sessionNo) {
        if (!StringUtils.hasText(sessionNo)) {
            return Optional.empty();
        }
        return jdbcClient.sql("""
                        SELECT session_no, scene, patient_id, register_id, doctor_id,
                               COALESCE(messages::text, '{}') AS messages
                        FROM ai_chat_session
                        WHERE session_no = :sessionNo AND delmark = 0
                        """)
                .param("sessionNo", sessionNo)
                .query((rs, rowNum) -> new StoredSession(
                        rs.getString("session_no"),
                        rs.getString("scene"),
                        rs.getObject("patient_id", Long.class),
                        rs.getObject("register_id", Long.class),
                        rs.getObject("doctor_id", Long.class),
                        readJsonMap(rs.getString("messages"))
                ))
                .optional();
    }

    public void updateMessages(String sessionNo, Object messages) {
        jdbcClient.sql("""
                        UPDATE ai_chat_session
                        SET messages = CAST(:messages AS jsonb),
                            update_time = NOW()
                        WHERE session_no = :sessionNo AND delmark = 0
                        """)
                .param("sessionNo", sessionNo)
                .param("messages", writeJson(messages))
                .update();
    }

    public int bindRegister(String sessionNo, Long registerId) {
        if (!StringUtils.hasText(sessionNo) || registerId == null) {
            return 0;
        }
        return jdbcClient.sql("""
                        UPDATE ai_chat_session
                        SET register_id = :registerId,
                            update_time = NOW()
                        WHERE session_no = :sessionNo
                          AND scene = 'TRIAGE'
                          AND delmark = 0
                        """)
                .param("sessionNo", sessionNo)
                .param("registerId", registerId)
                .update();
    }

    public record StoredSession(
            String sessionNo,
            String scene,
            Long patientId,
            Long registerId,
            Long doctorId,
            Map<String, Object> messages
    ) {
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String value) {
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return Map.of("messages", parsed);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
