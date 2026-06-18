package com.hospital.pacs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.pacs.client.HospitalAiClient;
import com.hospital.pacs.config.HospitalAiProperties;
import com.hospital.pacs.repository.CheckRequestRepository;
import com.hospital.pacs.repository.ImagingStudyRepository;
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
            return buildResultDetail(checkRequestId, check, study);
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
            return buildResultDetail(checkRequestId, check, study);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            imagingStudyRepository.markFailed(studyId, ex.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 影像分析失败: " + ex.getMessage());
        }
    }

    public Map<String, Object> getResultDetail(Long checkRequestId) {
        Map<String, Object> check = checkRequestRepository.findDetail(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));
        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId).orElse(null);
        return buildResultDetail(checkRequestId, check, study);
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

    private Map<String, Object> buildResultDetail(Long checkRequestId, Map<String, Object> check,
                                                  Map<String, Object> study) {
        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkRequestId);
        result.put("itemName", check.get("itemName"));
        result.put("medicalRecordNo", check.get("medicalRecordNo"));
        result.put("patientName", check.get("patientName"));
        result.put("status", check.get("status"));
        result.put("instrumentData", buildInstrumentData(check));
        result.put("doctorReportText", extractDoctorReportText(check.get("resultText")));
        result.put("reportTime", check.get("resultTime"));
        result.put("resultText", check.get("resultText"));
        result.put("resultAttachment", check.get("resultAttachment"));

        String aiReportStatus = "PENDING";
        String aiReportText = "";
        if (study != null) {
            String studyStatus = String.valueOf(study.get("status"));
            result.put("studyId", study.get("id"));
            result.put("studyStatus", studyStatus);
            result.put("modality", study.get("modality"));
            result.put("taskType", resolveTaskType(String.valueOf(study.get("modality"))));
            if ("COMPLETED".equals(studyStatus)) {
                aiReportStatus = "READY";
                aiReportText = String.valueOf(parseReportJson(study).getOrDefault("aiReportText", ""));
            } else if ("FAILED".equals(studyStatus)) {
                aiReportStatus = "FAILED";
                aiReportText = String.valueOf(study.getOrDefault("errorMessage", ""));
            }
        }
        result.put("aiReportStatus", aiReportStatus);
        result.put("aiReportText", aiReportText);
        return result;
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

    private String buildInstrumentData(Map<String, Object> check) {
        String bodyPart = check.get("bodyPart") == null ? "" : String.valueOf(check.get("bodyPart"));
        String purpose = check.get("purpose") == null ? "" : String.valueOf(check.get("purpose"));
        if (bodyPart.isBlank() && purpose.isBlank()) {
            return "";
        }
        if (bodyPart.isBlank()) {
            return purpose;
        }
        if (purpose.isBlank()) {
            return "检查部位: " + bodyPart;
        }
        return "检查部位: " + bodyPart + "\n检查目的: " + purpose;
    }

    private String extractDoctorReportText(Object resultText) {
        if (resultText == null) {
            return "";
        }
        String text = String.valueOf(resultText);
        int idx = text.indexOf("医师：");
        if (idx >= 0) {
            return text.substring(idx + 3).trim();
        }
        return text;
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
