package com.hospital.pacs.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.MedTechReportSupport.ParsedPublishedText;
import com.hospital.pacs.dto.CheckResultRequest;
import com.hospital.pacs.repository.CheckRequestRepository;
import com.hospital.pacs.security.AuthContextHolder;
import com.hospital.pacs.support.PacsAiReportCache;
import com.hospital.pacs.support.PacsReportStubSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PacsCheckService {

    private final CheckRequestRepository checkRequestRepository;
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
        return enrichResultDetail(loadResultDetailRow(checkRequestId));
    }

    public Map<String, Object> generateAiReport(Long checkRequestId) {
        Map<String, Object> row = loadResultDetailRow(checkRequestId);
        String itemName = (String) row.get("itemName");
        String aiReportText = PacsReportStubSupport.aiReportFor(itemName);
        pacsAiReportCache.put(checkRequestId, aiReportText, "READY");
        return enrichResultDetail(row);
    }

    @Transactional
    public Map<String, Object> saveResult(Long checkRequestId, CheckResultRequest request) {
        Long inputId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> row = checkRequestRepository.findByIdForUpdate(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));

        int status = ((Number) row.get("status")).intValue();
        if (status != InspectionRequestStatus.PAID && status != InspectionRequestStatus.EXECUTED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可录入结果");
        }

        String resultText = resolveResultText(request);
        if (resultText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写 AI 报告或医师意见");
        }

        checkRequestRepository.saveResult(checkRequestId, inputId, resultText, request.getResultAttachment());
        pacsAiReportCache.evict(checkRequestId);

        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkRequestId);
        result.put("status", InspectionRequestStatus.RESULT_READY);
        result.put("resultText", resultText);
        return result;
    }

    public Map<String, Object> imagingUploadStub() {
        Map<String, Object> result = new HashMap<>();
        result.put("stub", true);
        result.put("message", "MinIO/CNN 链路未启用，影像上传占位");
        result.put("studyStatus", "PENDING");
        return result;
    }

    private Map<String, Object> loadResultDetailRow(Long checkRequestId) {
        Map<String, Object> row = checkRequestRepository.findResultDetail(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));

        int currentStatus = ((Number) row.get("status")).intValue();
        if (currentStatus < InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "尚未缴费，无法查看");
        }
        return row;
    }

    private Map<String, Object> enrichResultDetail(Map<String, Object> row) {
        Map<String, Object> result = new HashMap<>(row);
        String itemName = (String) row.get("itemName");
        result.put("instrumentData", PacsReportStubSupport.instrumentDataFor(itemName));

        int status = ((Number) row.get("status")).intValue();
        Long checkRequestId = ((Number) row.get("checkRequestId")).longValue();

        if (status >= InspectionRequestStatus.RESULT_READY) {
            ParsedPublishedText parsed = PacsReportStubSupport.parsePublishedText((String) row.get("resultText"));
            result.put("aiReportText", parsed.aiReportText());
            result.put("doctorReportText", parsed.doctorReportText());
            result.put("aiReportStatus", parsed.aiReportText().isBlank() ? "PENDING" : "READY");
            return result;
        }

        PacsAiReportCache.Entry cached = pacsAiReportCache.get(checkRequestId);
        if (cached != null) {
            result.put("aiReportText", cached.aiReportText());
            result.put("aiReportStatus", cached.aiReportStatus());
        } else {
            result.put("aiReportText", "");
            result.put("aiReportStatus", "PENDING");
        }
        result.put("doctorReportText", "");
        return result;
    }

    private String resolveResultText(CheckResultRequest request) {
        if (request.getResultText() != null && !request.getResultText().isBlank()) {
            return request.getResultText().trim();
        }
        return PacsReportStubSupport.composeResultText(
                request.getAiReportText(),
                request.getDoctorReportText()
        );
    }
}
