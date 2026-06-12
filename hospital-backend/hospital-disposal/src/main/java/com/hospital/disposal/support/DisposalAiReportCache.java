package com.hospital.disposal.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DisposalAiReportCache {

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public void put(Long disposalRequestId, String aiReportText, String aiReportStatus) {
        cache.put(disposalRequestId, new Entry(aiReportText, aiReportStatus));
    }

    public Entry get(Long disposalRequestId) {
        return cache.get(disposalRequestId);
    }

    public void evict(Long disposalRequestId) {
        cache.remove(disposalRequestId);
    }

    public record Entry(String aiReportText, String aiReportStatus) {
    }
}
