package com.hospital.lis.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检验单 AI 报告草稿内存缓存（未发布前；重启后丢失，可接受于 STUB 阶段）。
 */
@Component
public class LisAiReportCache {

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public void put(Long inspectionRequestId, String aiReportText, String aiReportStatus) {
        cache.put(inspectionRequestId, new Entry(aiReportText, aiReportStatus));
    }

    public Entry get(Long inspectionRequestId) {
        return cache.get(inspectionRequestId);
    }

    public void evict(Long inspectionRequestId) {
        cache.remove(inspectionRequestId);
    }

    public record Entry(String aiReportText, String aiReportStatus) {
    }
}
