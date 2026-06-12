package com.hospital.aibridge.service;

import com.hospital.aibridge.domain.TriageMessage;
import com.hospital.aibridge.domain.TriageSession;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class TriageSessionStore {

    private static final int MAX_MESSAGES = 20;
    private static final Duration TTL = Duration.ofMinutes(30);

    private final ConcurrentMap<String, TriageSession> sessions = new ConcurrentHashMap<>();

    public TriageSession getOrCreate(String sessionId, Long patientId) {
        if (StringUtils.hasText(sessionId) && sessions.containsKey(sessionId)) {
            return sessions.get(sessionId);
        }
        String newSessionId = StringUtils.hasText(sessionId) ? sessionId : UUID.randomUUID().toString();
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
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> Duration.between(entry.getValue().getUpdateTime(), now).compareTo(TTL) > 0);
    }
}
