package com.hospital.disposal.service;



import com.hospital.common.constant.ErrorCode;

import com.hospital.common.constant.InspectionRequestStatus;

import com.hospital.common.exception.BusinessException;

import com.hospital.common.support.DisposalRecordComposer;

import com.hospital.common.support.MedTechSignSupport;

import com.hospital.disposal.dto.DisposalResultRequest;

import com.hospital.disposal.repository.DisposalRequestRepository;

import com.hospital.disposal.security.AuthContextHolder;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.util.HashMap;

import java.util.Map;



@Service

@RequiredArgsConstructor

public class DisposalExecuteService {



    private final DisposalRequestRepository disposalRequestRepository;



    public Map<String, Object> listQueue(Integer status, int page, int pageSize) {

        int offset = Math.max(page - 1, 0) * pageSize;

        Integer queryStatus = status != null ? status : InspectionRequestStatus.PAID;

        return Map.of(

                "list", disposalRequestRepository.findQueue(queryStatus, offset, pageSize),

                "page", page,

                "pageSize", pageSize

        );

    }



    @Transactional

    public Map<String, Object> execute(Long disposalRequestId) {

        Long executorId = AuthContextHolder.require().getEmployeeId();

        Map<String, Object> row = disposalRequestRepository.findByIdForUpdate(disposalRequestId)

                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));



        int currentStatus = ((Number) row.get("status")).intValue();

        if (currentStatus != InspectionRequestStatus.PAID) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费申请可执行");

        }



        disposalRequestRepository.markExecuted(disposalRequestId, executorId);



        Map<String, Object> result = new HashMap<>();

        result.put("disposalRequestId", disposalRequestId);

        result.put("status", InspectionRequestStatus.EXECUTED);

        return result;

    }



    public Map<String, Object> getResultDetail(Long disposalRequestId) {

        Map<String, Object> context = disposalRequestRepository.findDisposalRecordContext(disposalRequestId)

                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));

        requirePaidOrLater(context);

        return composeRecord(context, null, null);

    }



    @Transactional

    public Map<String, Object> saveResult(Long disposalRequestId, DisposalResultRequest request) {

        Long currentId = AuthContextHolder.require().getEmployeeId();

        Map<String, Object> locked = disposalRequestRepository.findByIdForUpdate(disposalRequestId)

                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));



        int currentStatus = ((Number) locked.get("status")).intValue();

        if (Boolean.TRUE.equals(request.getSignAsReviewerOnly())) {

            if (currentStatus < InspectionRequestStatus.EXECUTED) {

                throw new BusinessException(ErrorCode.BAD_REQUEST, "记录尚未录入，无法审核");

            }

        } else if (currentStatus != InspectionRequestStatus.PAID

                && currentStatus != InspectionRequestStatus.EXECUTED) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可录入结果");

        }



        Map<String, Object> context = disposalRequestRepository.findDisposalRecordContext(disposalRequestId)

                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));



        Long existingReporterId = context.get("resultInputId") != null

                ? ((Number) context.get("resultInputId")).longValue() : null;

        var sign = MedTechSignSupport.resolve(

                currentId,

                request.getSignAsReviewerOnly(),

                request.getPendingReview(),

                existingReporterId);



        String resultText = resolveResultText(request, context);

        if (!Boolean.TRUE.equals(request.getSignAsReviewerOnly()) && resultText.isBlank()) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写处置过程或观察与结果");

        }



        disposalRequestRepository.saveResult(

                disposalRequestId,

                sign.reporterId(),

                sign.reviewerId(),

                resultText,

                Boolean.TRUE.equals(request.getSignAsReviewerOnly()));



        Map<String, Object> result = new HashMap<>();

        result.put("disposalRequestId", disposalRequestId);

        result.put("status", sign.pendingReview()
                ? InspectionRequestStatus.EXECUTED
                : InspectionRequestStatus.RESULT_READY);

        result.put("resultText", resultText);

        return result;

    }



    private Map<String, Object> composeRecord(Map<String, Object> context, String processText, String outcomeText) {

        return DisposalRecordComposer.composeView(context, processText, outcomeText);

    }



    private void requirePaidOrLater(Map<String, Object> row) {

        int status = ((Number) row.get("status")).intValue();

        if (status < InspectionRequestStatus.PAID) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费及以后状态可查看记录");

        }

    }



    private String resolveResultText(DisposalResultRequest request, Map<String, Object> context) {

        if (Boolean.TRUE.equals(request.getSignAsReviewerOnly())) {

            return context.get("resultText") != null ? String.valueOf(context.get("resultText")).trim() : "";

        }

        if (request.getResultText() != null && !request.getResultText().isBlank()) {

            return request.getResultText().trim();

        }

        String process = request.getProcessText();

        String outcome = request.getOutcomeText();

        return DisposalRecordComposer.composeResultText(process, outcome).trim();

    }

}

