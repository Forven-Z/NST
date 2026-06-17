package com.hospital.aibridge.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
