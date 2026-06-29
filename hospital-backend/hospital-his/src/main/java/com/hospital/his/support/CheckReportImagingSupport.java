package com.hospital.his.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 imaging_study.report_json 组装检查报告读端所需的影像摘要（HIS 只读）。
 */
public final class CheckReportImagingSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> SNAPSHOT_PLANES = List.of("axial", "coronal", "sagittal");

    private CheckReportImagingSupport() {}

    public static Map<String, Object> buildImagingSummary(Long checkRequestId, Map<String, Object> study) {
        return buildImagingSummary(checkRequestId, study, false);
    }

    public static Map<String, Object> buildImagingSummary(
            Long checkRequestId,
            Map<String, Object> study,
            boolean forPatient) {
        Map<String, Object> imaging = new LinkedHashMap<>();
        if (study == null) {
            imaging.put("hasImaging", false);
            imaging.put("studyStatus", "NONE");
            imaging.put("hasSnapshots", false);
            return imaging;
        }
        String studyStatus = String.valueOf(study.get("status"));
        imaging.put("studyId", study.get("id"));
        imaging.put("studyStatus", studyStatus);
        imaging.put("modality", study.get("modality"));
        imaging.put("hasImaging", "COMPLETED".equals(studyStatus));
        if ("COMPLETED".equals(studyStatus) && !forPatient) {
            imaging.put("ctPreviewUrl", "/api/v1/pacs/imaging/preview/" + checkRequestId + "/ct");
            imaging.put("maskPreviewUrl", "/api/v1/pacs/imaging/preview/" + checkRequestId + "/mask");
        }
        Map<String, Object> json = parseJson(study.get("reportJson"));
        Object rawSnaps = json.get("reportSnapshots");
        if (rawSnaps instanceof Map<?, ?> snaps && !snaps.isEmpty()) {
            Map<String, String> reportImages = buildReportImageUrls(checkRequestId, snaps, forPatient);
            imaging.put("reportImages", reportImages);
            imaging.put("snapshotMeta", json.get("snapshotMeta"));
            imaging.put("hasSnapshots", !reportImages.isEmpty());
        } else {
            imaging.put("hasSnapshots", false);
        }
        return imaging;
    }

    private static Map<String, String> buildReportImageUrls(
            Long checkRequestId,
            Map<?, ?> snapKeys,
            boolean forPatient) {
        Map<String, String> urls = new LinkedHashMap<>();
        for (String plane : SNAPSHOT_PLANES) {
            if (snapKeys.containsKey(plane)) {
                urls.put(plane, forPatient
                        ? patientSnapshotUrl(checkRequestId, plane)
                        : staffSnapshotUrl(checkRequestId, plane));
            }
        }
        return urls;
    }

    private static String patientSnapshotUrl(Long checkRequestId, String plane) {
        return "/api/v1/patient/reports/exam/" + checkRequestId + "/snapshot/" + plane;
    }

    private static String staffSnapshotUrl(Long checkRequestId, String plane) {
        return "/api/v1/pacs/imaging/report-preview/" + checkRequestId + "/" + plane;
    }

    private static Map<String, Object> parseJson(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
