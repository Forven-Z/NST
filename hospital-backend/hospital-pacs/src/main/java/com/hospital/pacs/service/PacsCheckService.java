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

import java.util.Map;



@Service

@RequiredArgsConstructor

public class PacsCheckService {



    private final CheckRequestRepository checkRequestRepository;

    private final ImagingService imagingService;

    private final PacsAiReportCache pacsAiReportCache;



    public Map<String, Object> listQueue(Integer status, int page, int pageSize) {

        int offset = Math.max(page - 1, 0) * pageSize;

        Integer queryStatus = status != null ? status : InspectionRequestStatus.PAID;

        return Map.of(

                "list", checkRequestRepository.findQueue(queryStatus, offset, pageSize),

                "page", page,

                "pageSize", pageSize

        );

    }



    @Transactional

    public Map<String, Object> execute(Long checkRequestId) {

        Long executorId = AuthContextHolder.require().getEmployeeId();

        Map<String, Object> row = checkRequestRepository.findByIdForUpdate(checkRequestId)

                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));



        if (((Number) row.get("status")).intValue() != InspectionRequestStatus.PAID) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费申请可执行");

        }



        checkRequestRepository.markExecuted(checkRequestId, executorId);

        return Map.of("checkRequestId", checkRequestId, "status", InspectionRequestStatus.EXECUTED);

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

        if (Boolean.TRUE.equals(request.getSignAsReviewerOnly())) {

            if (status < InspectionRequestStatus.EXECUTED) {

                throw new BusinessException(ErrorCode.BAD_REQUEST, "报告尚未录入，无法审核");

            }

        } else if (status != InspectionRequestStatus.PAID && status != InspectionRequestStatus.EXECUTED) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可录入结果");

        }



        Long existingReporterId = context.get("resultInputId") != null

                ? ((Number) context.get("resultInputId")).longValue() : null;

        var sign = MedTechSignSupport.resolve(

                currentId,

                request.getSignAsReviewerOnly(),

                request.getPendingReview(),

                existingReporterId);



        String resultText = resolveResultText(request, context);

        if (!Boolean.TRUE.equals(request.getSignAsReviewerOnly()) && resultText.isBlank()) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "请生成 AI 报告或填写检查医师意见");

        }



        checkRequestRepository.saveResult(

                checkRequestId,

                sign.reporterId(),

                sign.reviewerId(),

                resultText,

                Boolean.TRUE.equals(request.getSignAsReviewerOnly()));

        pacsAiReportCache.evict(checkRequestId);



        Map<String, Object> result = new HashMap<>();

        result.put("checkRequestId", checkRequestId);

        result.put("status", sign.pendingReview()
                ? InspectionRequestStatus.EXECUTED
                : InspectionRequestStatus.RESULT_READY);
        result.put("resultText", resultText);
        return result;

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

