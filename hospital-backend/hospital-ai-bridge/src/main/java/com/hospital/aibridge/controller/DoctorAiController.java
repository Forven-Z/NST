package com.hospital.aibridge.controller;

import com.hospital.aibridge.dto.DiagnosisSuggestRequest;
import com.hospital.aibridge.dto.DoctorAiDraftUpdateRequest;
import com.hospital.aibridge.service.DoctorAiAssistService;
import com.hospital.aibridge.service.DoctorAiDraftService;
import com.hospital.common.Result;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/doctor")
public class DoctorAiController {

    private final DoctorAiAssistService aiAssistService;
    private final DoctorAiDraftService draftService;

    public DoctorAiController(DoctorAiAssistService aiAssistService, DoctorAiDraftService draftService) {
        this.aiAssistService = aiAssistService;
        this.draftService = draftService;
    }

    @PutMapping("/medical-records/{registerId}")
    public Result<Map<String, Object>> saveMedicalRecord(
            @PathVariable Long registerId,
            @RequestBody Map<String, Object> request) {
        return Result.success(draftService.saveMedicalRecord(registerId, request));
    }

    @PostMapping("/diagnosis/suggest")
    public Result<Map<String, Object>> diagnosisSuggest(@RequestBody DiagnosisSuggestRequest request) {
        return Result.success(aiAssistService.diagnosisSuggest(request));
    }

    @PostMapping("/check-requests/ai-draft")
    public Result<Map<String, Object>> createCheckDraft(@RequestBody Map<String, Object> request) {
        return Result.success(draftService.createClinicalDraft(request, "CHECK"));
    }

    @PostMapping("/inspection-requests/ai-draft")
    public Result<Map<String, Object>> createInspectionDraft(@RequestBody Map<String, Object> request) {
        return Result.success(draftService.createClinicalDraft(request, "INSPECTION"));
    }

    @PostMapping("/disposal-requests/ai-draft")
    public Result<Map<String, Object>> createDisposalDraft(@RequestBody Map<String, Object> request) {
        return Result.success(draftService.createClinicalDraft(request, "DISPOSAL"));
    }

    @PostMapping("/prescriptions/ai-draft")
    public Result<Map<String, Object>> createPrescriptionDraft(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Result.success(draftService.createPrescriptionDraft(request, authorization));
    }

    @PutMapping("/check-requests/ai-draft/{draftId}")
    public Result<Map<String, Object>> updateCheckDraft(
            @PathVariable Long draftId,
            @RequestBody DoctorAiDraftUpdateRequest request) {
        return Result.success(draftService.updateDraft(draftId, request));
    }

    @PutMapping("/inspection-requests/ai-draft/{draftId}")
    public Result<Map<String, Object>> updateInspectionDraft(
            @PathVariable Long draftId,
            @RequestBody DoctorAiDraftUpdateRequest request) {
        return Result.success(draftService.updateDraft(draftId, request));
    }

    @PutMapping("/disposal-requests/ai-draft/{draftId}")
    public Result<Map<String, Object>> updateDisposalDraft(
            @PathVariable Long draftId,
            @RequestBody DoctorAiDraftUpdateRequest request) {
        return Result.success(draftService.updateDraft(draftId, request));
    }

    @PutMapping("/prescriptions/ai-draft/{draftId}")
    public Result<Map<String, Object>> updatePrescriptionDraft(
            @PathVariable Long draftId,
            @RequestBody DoctorAiDraftUpdateRequest request) {
        return Result.success(draftService.updateDraft(draftId, request));
    }

    @PostMapping("/check-requests/ai-draft/{draftId}/confirm")
    public Result<Map<String, Object>> confirmCheckDraft(
            @PathVariable Long draftId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Result.success(draftService.confirmDraft(draftId, authorization));
    }

    @PostMapping("/inspection-requests/ai-draft/{draftId}/confirm")
    public Result<Map<String, Object>> confirmInspectionDraft(
            @PathVariable Long draftId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Result.success(draftService.confirmDraft(draftId, authorization));
    }

    @PostMapping("/disposal-requests/ai-draft/{draftId}/confirm")
    public Result<Map<String, Object>> confirmDisposalDraft(
            @PathVariable Long draftId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Result.success(draftService.confirmDraft(draftId, authorization));
    }

    @PostMapping("/prescriptions/ai-draft/{draftId}/confirm")
    public Result<Map<String, Object>> confirmPrescriptionDraft(
            @PathVariable Long draftId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return Result.success(draftService.confirmDraft(draftId, authorization));
    }

    private Long registerId(Map<String, Object> request) {
        Object value = request == null ? null : request.get("registerId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        throw new IllegalArgumentException("registerId 不能为空");
    }
}
