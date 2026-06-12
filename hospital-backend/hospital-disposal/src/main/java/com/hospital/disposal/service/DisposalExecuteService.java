package com.hospital.disposal.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.disposal.dto.DisposalResultRequest;
import com.hospital.disposal.repository.DisposalRequestRepository;
import com.hospital.disposal.security.AuthContextHolder;
import com.hospital.disposal.support.DisposalAiReportCache;
import com.hospital.disposal.support.DisposalReportStubSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisposalExecuteService {

    private final DisposalRequestRepository disposalRequestRepository;
    private final DisposalAiReportCache disposalAiReportCache;

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
        Map<String, Object> row = disposalRequestRepository.findResultDetail(disposalRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));
        requirePaidOrLater(row);
        return enrichResultDetail(row);
    }

    public Map<String, Object> generateAiReport(Long disposalRequestId) {
        Map<String, Object> row = disposalRequestRepository.findResultDetail(disposalRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));
        requirePaidOrLater(row);
        String itemName = (String) row.get("itemName");
        String aiText = DisposalReportStubSupport.aiReportFor(itemName);
        disposalAiReportCache.put(disposalRequestId, aiText, "READY");
        return enrichResultDetail(row);
    }

    @Transactional
    public Map<String, Object> saveResult(Long disposalRequestId, DisposalResultRequest request) {
        Long inputId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> row = disposalRequestRepository.findByIdForUpdate(disposalRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));

        int currentStatus = ((Number) row.get("status")).intValue();
        if (currentStatus != InspectionRequestStatus.PAID
                && currentStatus != InspectionRequestStatus.EXECUTED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可录入结果");
        }

        String resultText = resolveResultText(request);
        if (resultText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写结果文本或 AI/医师意见");
        }

        disposalRequestRepository.saveResult(
                disposalRequestId,
                inputId,
                resultText,
                request.getResultAttachment()
        );
        disposalAiReportCache.evict(disposalRequestId);

        Map<String, Object> result = new HashMap<>();
        result.put("disposalRequestId", disposalRequestId);
        result.put("status", InspectionRequestStatus.RESULT_READY);
        result.put("resultText", resultText);
        return result;
    }

    private void requirePaidOrLater(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费及以后状态可查看报告");
        }
    }

    private Map<String, Object> enrichResultDetail(Map<String, Object> row) {
        Long id = ((Number) row.get("disposalRequestId")).longValue();
        String itemName = (String) row.get("itemName");
        String resultText = row.get("resultText") != null ? String.valueOf(row.get("resultText")) : "";

        var parsed = DisposalReportStubSupport.parsePublishedText(resultText);
        String aiReportText = parsed.aiReportText();
        String doctorReportText = parsed.doctorReportText();
        String aiReportStatus = "PENDING";

        if (aiReportText.isBlank()) {
            DisposalAiReportCache.Entry cached = disposalAiReportCache.get(id);
            if (cached != null) {
                aiReportText = cached.aiReportText();
                aiReportStatus = cached.aiReportStatus();
            }
        } else {
            aiReportStatus = "READY";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("disposalRequestId", id);
        result.put("status", row.get("status"));
        result.put("itemName", itemName);
        result.put("patientName", row.get("patientName"));
        result.put("medicalRecordNo", row.get("medicalRecordNo"));
        result.put("resultText", resultText);
        result.put("resultAttachment", row.get("resultAttachment"));
        result.put("reportTime", row.get("resultTime"));
        result.put("instrumentData", DisposalReportStubSupport.instrumentDataFor(itemName));
        result.put("aiReportText", aiReportText);
        result.put("doctorReportText", doctorReportText);
        result.put("aiReportStatus", aiReportStatus);
        return result;
    }

    private String resolveResultText(DisposalResultRequest request) {
        if (request.getResultText() != null && !request.getResultText().isBlank()) {
            return request.getResultText().trim();
        }
        return DisposalReportStubSupport.composeResultText(
                request.getAiReportText(), request.getDoctorReportText()).trim();
    }
}
