package com.hospital.pacs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.pacs.client.AiBridgeReportClient;
import com.hospital.pacs.client.HospitalAiClient;
import com.hospital.common.support.CheckReportComposer;
import com.hospital.pacs.config.HospitalAiProperties;
import com.hospital.pacs.repository.CheckRequestRepository;
import com.hospital.pacs.repository.EmployeeRepository;
import com.hospital.pacs.repository.ImagingStudyRepository;
import com.hospital.pacs.security.AuthContextHolder;
import com.hospital.pacs.support.PacsAiReportCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImagingService {

    private final CheckRequestRepository checkRequestRepository;
    private final ImagingStudyRepository imagingStudyRepository;
    private final MinioStorageService minioStorageService;
    private final HospitalAiClient hospitalAiClient;
    private final HospitalAiProperties hospitalAiProperties;
    private final ImagingCallbackRegistry imagingCallbackRegistry;
    private final ObjectMapper objectMapper;
    private final PacsAiReportCache pacsAiReportCache;
    private final EmployeeRepository employeeRepository;
    private final AiBridgeReportClient aiBridgeReportClient;

    @Transactional
    public Map<String, Object> uploadImaging(Long checkRequestId, MultipartFile[] files) {
        Map<String, Object> check = checkRequestRepository.findDetail(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));

        int status = ((Number) check.get("status")).intValue();
        if (status < InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费检查可上传影像");
        }

        minioStorageService.uploadStudySources(checkRequestId, files);
        String sourcePrefix = minioStorageService.studySourcePrefix(checkRequestId);
        String modality = inferModality(check);

        long studyId;
        var existing = imagingStudyRepository.findByCheckRequestId(checkRequestId);
        if (existing.isPresent()) {
            studyId = ((Number) existing.get().get("id")).longValue();
            imagingStudyRepository.resetToPending(studyId, minioStorageService.bucket(), sourcePrefix);
            imagingStudyRepository.updateModality(studyId, modality);
        } else {
            studyId = imagingStudyRepository.insertPending(
                    checkRequestId,
                    ((Number) check.get("registerId")).longValue(),
                    ((Number) check.get("patientId")).longValue(),
                    modality,
                    minioStorageService.bucket(),
                    sourcePrefix
            );
        }

        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkRequestId);
        result.put("studyId", studyId);
        result.put("studyStatus", "PENDING");
        result.put("uploadStatus", "UPLOADED");
        result.put("sourceObjectKey", sourcePrefix);
        return result;
    }

    public Map<String, Object> generateAiReport(Long checkRequestId) {
        Map<String, Object> check = checkRequestRepository.findDetail(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));

        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "请先上传影像"));

        long studyId = ((Number) study.get("id")).longValue();
        String modality = resolveStudyModality(check, study);
        if (!modality.equals(String.valueOf(study.get("modality")))) {
            imagingStudyRepository.updateModality(studyId, modality);
            study.put("modality", modality);
        }
        String taskType = resolveTaskType(modality);
        String status = String.valueOf(study.get("status"));

        if ("COMPLETED".equals(status)) {
            return getStructuredResultDetail(checkRequestId);
        }

        if ("FAILED".equals(status)) {
            imagingStudyRepository.resetToPending(
                    studyId,
                    String.valueOf(study.get("sourceBucket")),
                    String.valueOf(study.get("sourceObjectKey"))
            );
            status = "PENDING";
        }

        if (!"PROCESSING".equals(status)) {
            imagingCallbackRegistry.register(checkRequestId);
            imagingStudyRepository.markProcessing(studyId);
            try {
                hospitalAiClient.submitInferenceJob(
                        studyId,
                        checkRequestId,
                        String.valueOf(study.get("sourceBucket")),
                        String.valueOf(study.get("sourceObjectKey")),
                        minioStorageService.studyResultPrefix(checkRequestId),
                        taskType
                );
            } catch (Exception ex) {
                imagingStudyRepository.markFailed(studyId, ex.getMessage());
                imagingCallbackRegistry.fail(checkRequestId, "提交 AI 推理任务失败: " + ex.getMessage());
                throw new BusinessException(ErrorCode.BAD_REQUEST, "提交 AI 推理任务失败: " + ex.getMessage());
            }
        } else {
            imagingCallbackRegistry.register(checkRequestId);
        }

        try {
            Map<String, Object> callback = imagingCallbackRegistry.await(
                    checkRequestId, hospitalAiProperties.getInferenceTimeoutSeconds());
            persistCallbackResult(studyId, callback);
            study = imagingStudyRepository.findById(studyId).orElse(study);
            return getStructuredResultDetail(checkRequestId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            imagingStudyRepository.markFailed(studyId, ex.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 影像分析失败: " + ex.getMessage());
        }
    }

    public Map<String, Object> getStructuredResultDetail(Long checkRequestId) {
        Map<String, Object> context = checkRequestRepository.findReportContext(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));
        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId).orElse(null);
        return enrichCheckReport(context, study);
    }

    /**
     * LLM 诊断印象：仅基于医师填写的 CT 所见；CNN 推理请走 {@link #generateAiReport}。
     */
    public Map<String, Object> generateLlmReport(Long checkRequestId, String findingsText) {
        String findings = findingsText != null ? findingsText.trim() : "";
        if (findings.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先填写 CT 所见");
        }

        Map<String, Object> context = checkRequestRepository.findReportContext(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));
        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId).orElse(null);

        String aiText = aiBridgeReportClient.generateHeadCtImpression(context, findings);
        pacsAiReportCache.put(checkRequestId, aiText, "READY");

        return enrichCheckReport(context, study, findings, aiText, null, "READY");
    }

    public Map<String, Object> getResultDetail(Long checkRequestId) {
        return getStructuredResultDetail(checkRequestId);
    }

    public Map<String, Object> getImagingPreview(Long checkRequestId) {
        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "尚未上传影像"));

        if (!"COMPLETED".equals(String.valueOf(study.get("status")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 分析尚未完成，暂无预览");
        }

        String prefix = minioStorageService.studyResultPrefix(checkRequestId);
        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkRequestId);
        result.put("studyId", study.get("id"));
        result.put("studyStatus", study.get("status"));
        result.put("ctPreviewUrl", "/api/v1/pacs/imaging/preview/" + checkRequestId + "/ct");
        result.put("maskPreviewUrl", "/api/v1/pacs/imaging/preview/" + checkRequestId + "/mask");
        result.put("maskObjectKey", prefix + "mask.nii.gz");
        result.put("previewObjectKey", prefix + "ct_preview.nii.gz");
        parseReportJson(study).forEach(result::putIfAbsent);
        return result;
    }

    public InputStream openPreviewStream(Long checkRequestId, String kind) throws Exception {
        imagingStudyRepository.findByCheckRequestId(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "尚未上传影像"));

        String prefix = minioStorageService.studyResultPrefix(checkRequestId);
        String objectKey = "mask".equalsIgnoreCase(kind) ? prefix + "mask.nii.gz" : prefix + "ct_preview.nii.gz";
        return minioStorageService.openObject(objectKey);
    }

    @Transactional
    public Map<String, Object> saveReportSnapshots(
            Long checkRequestId,
            MultipartFile axial,
            MultipartFile coronal,
            MultipartFile sagittal,
            String metaJson) {
        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "尚未上传影像，无法采图"));

        long studyId = ((Number) study.get("id")).longValue();
        String prefix = minioStorageService.reportSnapshotPrefix(checkRequestId);
        Map<String, String> keys = new java.util.LinkedHashMap<>();
        storeSnapshot(prefix, "axial", axial, keys);
        storeSnapshot(prefix, "coronal", coronal, keys);
        storeSnapshot(prefix, "sagittal", sagittal, keys);

        if (keys.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未收到有效采图");
        }

        try {
            Map<String, Object> merged = new HashMap<>(parseReportJson(study));
            merged.put("reportSnapshots", keys);
            if (metaJson != null && !metaJson.isBlank()) {
                merged.put("snapshotMeta", objectMapper.readValue(metaJson, new TypeReference<>() {}));
            }
            imagingStudyRepository.mergeReportJson(studyId, objectMapper.writeValueAsString(merged));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "保存报告采图失败: " + ex.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkRequestId);
        result.put("reportImages", buildReportImageUrls(checkRequestId, keys));
        return result;
    }

    public InputStream openReportSnapshotStream(Long checkRequestId, String plane) throws Exception {
        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "尚未上传影像"));
        Map<String, Object> json = parseReportJson(study);
        Object raw = json.get("reportSnapshots");
        if (!(raw instanceof Map<?, ?> snaps)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告采图不存在");
        }
        Object key = snaps.get(plane);
        if (key == null || String.valueOf(key).isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告采图不存在: " + plane);
        }
        return minioStorageService.openObject(String.valueOf(key));
    }

    public void handleCallback(Map<String, Object> payload) {
        long studyId = ((Number) payload.get("studyId")).longValue();
        Map<String, Object> study = imagingStudyRepository.findById(studyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "影像任务不存在"));

        Long checkRequestId = ((Number) study.get("checkRequestId")).longValue();
        String status = String.valueOf(payload.get("status"));

        if ("SUCCEEDED".equals(status)) {
            imagingCallbackRegistry.complete(checkRequestId, payload);
            return;
        }

        String error = payload.get("errorMessage") == null
                ? (payload.get("error") == null ? "CNN 推理失败" : String.valueOf(payload.get("error")))
                : String.valueOf(payload.get("errorMessage"));
        imagingStudyRepository.markFailed(studyId, error);
        imagingCallbackRegistry.fail(checkRequestId, error);
    }

    public Map<String, Object> listImagingStudies(
            String statusFilter, Long patientId, String medicalRecordNo, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        String dbStatus = mapStatusFilter(statusFilter);
        List<Map<String, Object>> list = imagingStudyRepository.listStudies(
                dbStatus, patientId, medicalRecordNo, offset, pageSize);
        return Map.of("list", list, "page", page, "pageSize", pageSize);
    }

    private String mapStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        return switch (statusFilter) {
            case "IN_PROGRESS" -> "PROCESSING";
            case "COMPLETED" -> "COMPLETED";
            case "PENDING" -> "PENDING";
            default -> statusFilter;
        };
    }

    private void persistCallbackResult(long studyId, Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) payload.get("result");
        if (result == null) {
            throw new IllegalStateException("回调缺少 result");
        }
        try {
            Map<String, Object> merged = new HashMap<>();
            Object reportJson = result.get("reportJson");
            if (reportJson instanceof Map<?, ?> reportMap) {
                reportMap.forEach((k, v) -> merged.put(String.valueOf(k), v));
            }
            merged.put("previewObjectKey", result.get("previewObjectKey"));
            merged.put("maskObjectKey", result.get("maskObjectKey"));
            String reportJsonText = objectMapper.writeValueAsString(merged);
            imagingStudyRepository.markCompleted(
                    studyId,
                    String.valueOf(result.getOrDefault("maskBucket", result.get("resultBucket"))),
                    String.valueOf(result.get("maskObjectKey")),
                    String.valueOf(result.get("previewObjectKey")),
                    reportJsonText
            );
        } catch (Exception e) {
            throw new IllegalStateException("持久化 CNN 回调结果失败", e);
        }
    }

    private Map<String, Object> enrichCheckReport(Map<String, Object> context, Map<String, Object> study) {
        return enrichCheckReport(context, study, null, null, null, null);
    }

    private Map<String, Object> enrichCheckReport(
            Map<String, Object> context,
            Map<String, Object> study,
            String findingsOverride,
            String aiOverride,
            String doctorOverride,
            String aiStatusOverride) {

        Long checkRequestId = ((Number) context.get("checkRequestId")).longValue();
        String resultText = context.get("resultText") != null ? String.valueOf(context.get("resultText")) : "";
        var parsed = CheckReportComposer.parsePublishedText(resultText);

        String findings;
        if (findingsOverride != null) {
            findings = findingsOverride.trim();
        } else {
            findings = parsed.findingsText();
        }

        String ai = aiOverride != null ? aiOverride : parsed.aiReportText();
        String doctor = doctorOverride != null ? doctorOverride : parsed.doctorReportText();
        String aiStatus = aiStatusOverride;

        if (ai.isBlank()) {
            PacsAiReportCache.Entry cached = pacsAiReportCache.get(checkRequestId);
            if (cached != null) {
                ai = cached.aiReportText();
                aiStatus = cached.aiReportStatus();
            }
        } else if (aiStatus == null) {
            aiStatus = "READY";
        }
        if (aiStatus == null || aiStatus.isBlank()) {
            aiStatus = ai.isBlank() ? "PENDING" : "READY";
        }

        return CheckReportComposer.composeView(
                context, findings, ai, doctor, aiStatus, buildImagingMeta(checkRequestId, study));
    }

    private Map<String, Object> buildImagingMeta(Long checkRequestId, Map<String, Object> study) {
        Map<String, Object> imaging = new HashMap<>();
        if (study == null) {
            imaging.put("hasImaging", false);
            imaging.put("studyStatus", "NONE");
            return imaging;
        }
        String studyStatus = String.valueOf(study.get("status"));
        imaging.put("studyId", study.get("id"));
        imaging.put("studyStatus", studyStatus);
        imaging.put("modality", study.get("modality"));
        imaging.put("hasImaging", "COMPLETED".equals(studyStatus));
        if ("COMPLETED".equals(studyStatus)) {
            imaging.put("ctPreviewUrl", "/api/v1/pacs/imaging/preview/" + checkRequestId + "/ct");
            imaging.put("maskPreviewUrl", "/api/v1/pacs/imaging/preview/" + checkRequestId + "/mask");
        }
        Map<String, Object> json = study != null ? parseReportJson(study) : Map.of();
        Object rawSnaps = json.get("reportSnapshots");
        if (rawSnaps instanceof Map<?, ?> snaps && !snaps.isEmpty()) {
            Map<String, String> reportImages = buildReportImageUrls(checkRequestId, snaps);
            imaging.put("reportImages", reportImages);
            imaging.put("snapshotMeta", json.get("snapshotMeta"));
        }
        return imaging;
    }

    private Map<String, String> buildReportImageUrls(Long checkRequestId, Map<?, ?> snapKeys) {
        Map<String, String> urls = new java.util.LinkedHashMap<>();
        for (Object plane : List.of("axial", "coronal", "sagittal")) {
            if (snapKeys.containsKey(plane)) {
                urls.put(String.valueOf(plane),
                        "/api/v1/pacs/imaging/report-preview/" + checkRequestId + "/" + plane);
            }
        }
        return urls;
    }

    private void storeSnapshot(String prefix, String plane, MultipartFile file, Map<String, String> keys) {
        if (file == null || file.isEmpty()) {
            return;
        }
        try {
            String objectKey = prefix + plane + ".png";
            minioStorageService.uploadBytes(objectKey, file.getBytes(), "image/png");
            keys.put(plane, objectKey);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传采图失败: " + plane);
        }
    }


    private Map<String, Object> parseReportJson(Map<String, Object> study) {
        Object raw = study.get("reportJson");
        if (raw == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String resolveStudyModality(Map<String, Object> check, Map<String, Object> study) {
        String stored = study.get("modality") == null ? "" : String.valueOf(study.get("modality"));
        if ("CT_HEAD".equals(stored) || "CT_LUNG".equals(stored) || "TUMOR_SEG".equals(stored)) {
            return stored;
        }
        return inferModality(check);
    }

    private String inferModality(Map<String, Object> check) {
        String blob = joinCheckText(check);
        if (containsAny(blob, "胸", "肺", "CHEST", "LUNG")) {
            return "CT_LUNG";
        }
        if (containsAny(blob, "肿瘤", "病灶", "肿物", "TUMOR")) {
            return "TUMOR_SEG";
        }
        if (containsAny(blob, "头", "颅", "脑", "HEAD")) {
            return "CT_HEAD";
        }
        if (blob.contains("CT")) {
            return "CT_HEAD";
        }
        return "CT_HEAD";
    }

    private String resolveTaskType(String modality) {
        if (modality == null) {
            return "HEAD_CT_ARTIFACT";
        }
        return switch (modality) {
            case "CT_LUNG" -> "LUNG_CT_ARTIFACT";
            case "TUMOR_SEG" -> "TUMOR_SEG";
            default -> "HEAD_CT_ARTIFACT";
        };
    }

    private String joinCheckText(Map<String, Object> check) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, check.get("itemName"));
        appendField(sb, check.get("bodyPart"));
        appendField(sb, check.get("purpose"));
        return sb.toString().toUpperCase();
    }

    private void appendField(StringBuilder sb, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            sb.append(text);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
