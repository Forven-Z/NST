package com.hospital.pacs.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检查单 AI 报告草稿内存缓存（未发布前；重启后丢失，可接受于 STUB 阶段）。
 */
@Component
public class PacsAiReportCache {

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public void put(Long checkRequestId, String aiReportText, String aiReportStatus) {
        cache.put(checkRequestId, new Entry(aiReportText, aiReportStatus));
    }

    public Entry get(Long checkRequestId) {
        return cache.get(checkRequestId);
    }

    public void evict(Long checkRequestId) {
        cache.remove(checkRequestId);
    }

    public record Entry(String aiReportText, String aiReportStatus) {
    }
}
