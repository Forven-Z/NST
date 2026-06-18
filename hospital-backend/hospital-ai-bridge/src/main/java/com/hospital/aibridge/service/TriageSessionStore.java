package com.hospital.aibridge.service;

import com.hospital.aibridge.domain.TriageMessage;
import com.hospital.aibridge.domain.TriageSession;
import com.hospital.aibridge.domain.TriageStage;
import com.hospital.aibridge.repository.AiChatSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TriageSessionStore {

    private static final int MAX_MESSAGES = 20;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String TRIAGE_SCENE = "TRIAGE";

    private final ConcurrentMap<String, TriageSession> sessions = new ConcurrentHashMap<>();
    private final AiChatSessionRepository chatSessionRepository;

    public TriageSessionStore(AiChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    public TriageSession getOrCreate(String sessionId, Long patientId) {
        if (StringUtils.hasText(sessionId) && sessions.containsKey(sessionId)) {
            return sessions.get(sessionId);
        }
        if (StringUtils.hasText(sessionId)) {
            TriageSession restored = restoreFromDatabase(sessionId);
            if (restored != null) {
                sessions.put(restored.getSessionId(), restored);
                return restored;
            }
        }

        String newSessionId = StringUtils.hasText(sessionId) ? sessionId : createDatabaseSession(patientId);
        TriageSession session = new TriageSession(newSessionId, patientId);
        sessions.put(newSessionId, session);
        return session;
    }

    public void addMessage(TriageSession session, String role, String content) {
        session.getMessages().add(new TriageMessage(role, content, Instant.now()));
        while (session.getMessages().size() > MAX_MESSAGES) {
            session.getMessages().remove(0);
        }
        session.touch();
        persist(session);
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> Duration.between(entry.getValue().getUpdateTime(), now).compareTo(TTL) > 0);
    }

    private String createDatabaseSession(Long patientId) {
        try {
            return chatSessionRepository.insertSessionPayload(TRIAGE_SCENE, null, patientId, null, payload(new TriageSession("", patientId)));
        } catch (Exception ex) {
            return "TR" + Instant.now().toEpochMilli() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        }
    }

    private TriageSession restoreFromDatabase(String sessionId) {
        try {
            return chatSessionRepository.findActiveBySessionNo(sessionId)
                    .filter(row -> TRIAGE_SCENE.equals(row.scene()))
                    .map(row -> hydrate(row.sessionNo(), row.patientId(), row.messages()))
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private TriageSession hydrate(String sessionId, Long patientId, Map<String, Object> payload) {
        TriageSession session = new TriageSession(sessionId, patientId);
        Object summary = payload.get("summary");
        if (summary instanceof String value) {
            session.setSummary(value);
        }
        Object stage = payload.get("stage");
        if (stage instanceof String value) {
            try {
                session.setStage(TriageStage.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                session.setStage(TriageStage.ASKING);
            }
        }
        for (Map<String, Object> item : messageMaps(payload.get("messages"))) {
            String role = stringValue(item.get("role"));
            String content = stringValue(item.get("content"));
            Instant time = parseInstant(stringValue(item.get("time")));
            if (StringUtils.hasText(role) && StringUtils.hasText(content)) {
                session.getMessages().add(new TriageMessage(role, content, time));
            }
        }
        return session;
    }

    private void persist(TriageSession session) {
        try {
            chatSessionRepository.updateMessages(session.getSessionId(), payload(session));
        } catch (Exception ignored) {
            // DB persistence is best-effort; in-memory session keeps the current request path available.
        }
    }

    private Map<String, Object> payload(TriageSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", session.getStage().name());
        payload.put("round", session.getRound());
        payload.put("summary", session.getSummary());
        payload.put("recommendedDepartments", session.getRecommendedDepartments());
        payload.put("messages", session.getMessages().stream().map(this::messagePayload).toList());
        return payload;
    }

    private Map<String, Object> messagePayload(TriageMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("role", message.role());
        row.put("content", message.content());
        row.put("time", message.time().toString());
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messageMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}
