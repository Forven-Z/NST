package com.hospital.pacs.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.CheckReportComposer;
import com.hospital.common.support.MedTechSignSupport;
import com.hospital.pacs.dto.CheckResultRequest;
import com.hospital.pacs.repository.CheckRequestRepository;
import com.hospital.pacs.security.AuthContextHolder;
import com.hospital.pacs.support.PacsAiReportCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PacsCheckService {

    private final CheckRequestRepository checkRequestRepository;
    private final ImagingService imagingService;
    private final PacsAiReportCache pacsAiReportCache;

    @Transactional
    public Map<String, Object> listQueue(Integer status, int page, int pageSize) {
        autoAssignPaidRequests();
        int offset = Math.max(page - 1, 0) * pageSize;
        Integer queryStatus = status != null ? status : InspectionRequestStatus.PAID;
        Long executorId = queueExecutorFilter();
        return Map.of(
                "list", checkRequestRepository.findQueue(queryStatus, executorId, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    @Transactional
    public Map<String, Object> execute(Long checkRequestId) {
        return executeOrder(checkRequestId);
    }

    public Map<String, Object> getResultDetail(Long checkRequestId) {
        return imagingService.getStructuredResultDetail(checkRequestId);
    }

    public Map<String, Object> generateLlmReport(Long checkRequestId, String findingsText) {
        return imagingService.generateLlmReport(checkRequestId, findingsText);
    }

    @Transactional
    public Map<String, Object> saveResult(Long checkRequestId, CheckResultRequest request) {
        Long currentId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> context = checkRequestRepository.findReportContext(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));

        int status = ((Number) context.get("status")).intValue();
        boolean signAsReviewerOnly = Boolean.TRUE.equals(request.getSignAsReviewerOnly());
        assertCanSaveResult(status, signAsReviewerOnly);

        Long existingReporterId = context.get("resultInputId") != null
                ? ((Number) context.get("resultInputId")).longValue() : null;
        var sign = MedTechSignSupport.resolve(
                currentId,
                request.getSignAsReviewerOnly(),
                request.getPendingReview(),
                existingReporterId);

        String resultText = resolveResultText(request, context);
        if (!signAsReviewerOnly && resultText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请生成 AI 报告或填写检查医师意见");
        }

        int targetStatus = resolveSaveResultTarget(status, signAsReviewerOnly, sign.pendingReview());
        applySaveResultStatus(checkRequestId, status, targetStatus);

        checkRequestRepository.saveResultContent(
                checkRequestId,
                sign.reporterId(),
                sign.reviewerId(),
                resultText,
                signAsReviewerOnly);
        pacsAiReportCache.evict(checkRequestId);

        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkRequestId);
        result.put("status", targetStatus);
        result.put("resultText", resultText);
        return result;
    }

    @Override
    protected MedTechExecuteCoordinator coordinator() {
        return pacsMedTechOrderCoordinator;
    }

    @Override
    protected Long requireExecutorId() {
        return AuthContextHolder.require().getEmployeeId();
    }

    @Override
    protected String orderIdResultKey() {
        return "checkRequestId";
    }

    private String resolveResultText(CheckResultRequest request, Map<String, Object> context) {
        if (Boolean.TRUE.equals(request.getSignAsReviewerOnly())) {
            return context.get("resultText") != null ? String.valueOf(context.get("resultText")).trim() : "";
        }
        if (request.getResultText() != null && !request.getResultText().isBlank()) {
            return request.getResultText().trim();
        }

        String findings = request.getFindingsText();
        String ai = request.getAiReportText();
        String doctor = request.getDoctorReportText();

        if (ai == null || ai.isBlank()) {
            PacsAiReportCache.Entry cached = pacsAiReportCache.get(toLong(context.get("checkRequestId")));
            if (cached != null) {
                ai = cached.aiReportText();
            }
        }

        if ((findings == null || findings.isBlank()) && (ai == null || ai.isBlank())
                && (doctor == null || doctor.isBlank())) {
            return "";
        }

        return CheckReportComposer.composeResultText(findings, ai, doctor);
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
