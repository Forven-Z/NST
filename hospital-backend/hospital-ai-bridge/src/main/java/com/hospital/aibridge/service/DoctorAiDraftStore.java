package com.hospital.aibridge.service;

import com.hospital.aibridge.domain.DoctorAiDraft;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DoctorAiDraftStore {

    private final AtomicLong draftId = new AtomicLong(9000);
    private final Map<Long, DoctorAiDraft> drafts = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Object>> medicalRecords = new ConcurrentHashMap<>();

    public Map<String, Object> saveMedicalRecord(Long registerId, Map<String, Object> record) {
        Map<String, Object> snapshot = new LinkedHashMap<>(record == null ? Map.of() : record);
        snapshot.put("registerId", registerId);
        snapshot.put("savedAt", OffsetDateTime.now());
        medicalRecords.put(registerId, snapshot);
        return snapshot;
    }

    public Optional<Map<String, Object>> findMedicalRecord(Long registerId) {
        return Optional.ofNullable(medicalRecords.get(registerId));
    }

    public DoctorAiDraft save(DoctorAiDraft draft) {
        OffsetDateTime now = OffsetDateTime.now();
        if (draft.getId() == null) {
            draft.setId(draftId.incrementAndGet());
            draft.setCreateTime(now);
        }
        draft.setUpdateTime(now);
        drafts.put(draft.getId(), draft);
        return draft;
    }

    public Optional<DoctorAiDraft> find(Long id) {
        return Optional.ofNullable(drafts.get(id));
    }
}
