package com.hospital.lis.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.CriticalValueParser;
import com.hospital.common.support.MedTechReportSupport.ParsedPublishedText;
import com.hospital.lis.dto.InspectionResultRequest;
import com.hospital.lis.repository.InspectionRequestRepository;
import com.hospital.lis.security.AuthContextHolder;
import com.hospital.lis.support.LisAiReportCache;
import com.hospital.lis.support.LisReportStubSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LisInspectionService {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final LisAiReportCache lisAiReportCache;

    public Map<String, Object> listQueue(Integer status, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        Integer queryStatus = status != null ? status : InspectionRequestStatus.PAID;
        return Map.of(
                "list", inspectionRequestRepository.findQueue(queryStatus, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    @Transactional
    public Map<String, Object> execute(Long inspectionRequestId) {
        Long executorId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> row = inspectionRequestRepository.findByIdForUpdate(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));

        int currentStatus = ((Number) row.get("status")).intValue();
        if (currentStatus != InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已缴费申请可执行");
        }

        inspectionRequestRepository.markExecuted(inspectionRequestId, executorId);

        Map<String, Object> result = new HashMap<>();
        result.put("inspectionRequestId", inspectionRequestId);
        result.put("status", InspectionRequestStatus.EXECUTED);
        return result;
    }

    public Map<String, Object> getResultDetail(Long inspectionRequestId) {
        return enrichResultDetail(loadResultDetailRow(inspectionRequestId));
    }

    public Map<String, Object> generateAiReport(Long inspectionRequestId) {
        Map<String, Object> row = loadResultDetailRow(inspectionRequestId);
        String itemName = (String) row.get("itemName");
        String aiReportText = LisReportStubSupport.aiReportFor(itemName);
        lisAiReportCache.put(inspectionRequestId, aiReportText, "READY");
        return enrichResultDetail(row);
    }

    @Transactional
    public Map<String, Object> saveResult(Long inspectionRequestId, InspectionResultRequest request) {
        Long inputId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> row = inspectionRequestRepository.findByIdForUpdate(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));

        int currentStatus = ((Number) row.get("status")).intValue();
        if (currentStatus != InspectionRequestStatus.PAID
                && currentStatus != InspectionRequestStatus.EXECUTED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可录入结果");
        }

        String resultText = resolveResultText(request);
        if (resultText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写 AI 报告或医师意见");
        }

        inspectionRequestRepository.saveResult(
                inspectionRequestId,
                inputId,
                resultText,
                request.getResultAttachment()
        );
        lisAiReportCache.evict(inspectionRequestId);

        Map<String, Object> result = new HashMap<>();
        result.put("inspectionRequestId", inspectionRequestId);
        result.put("status", InspectionRequestStatus.RESULT_READY);
        result.put("resultText", resultText);
        return result;
    }

    private Map<String, Object> loadResultDetailRow(Long inspectionRequestId) {
        Map<String, Object> row = inspectionRequestRepository.findResultDetail(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));

        int currentStatus = ((Number) row.get("status")).intValue();
        if (currentStatus < InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "尚未缴费，无法查看");
        }
        return row;
    }

    private Map<String, Object> enrichResultDetail(Map<String, Object> row) {
        Map<String, Object> result = new HashMap<>(row);
        String itemName = (String) row.get("itemName");
        String instrumentData = LisReportStubSupport.instrumentDataFor(itemName);
        result.put("instrumentData", instrumentData);
        result.put("criticalItems", CriticalValueParser.parse(instrumentData));

        int status = ((Number) row.get("status")).intValue();
        Long inspectionRequestId = ((Number) row.get("inspectionRequestId")).longValue();

        if (status >= InspectionRequestStatus.RESULT_READY) {
            ParsedPublishedText parsed = LisReportStubSupport.parsePublishedText((String) row.get("resultText"));
            result.put("aiReportText", parsed.aiReportText());
            result.put("doctorReportText", parsed.doctorReportText());
            result.put("aiReportStatus", parsed.aiReportText().isBlank() ? "PENDING" : "READY");
            return result;
        }

        LisAiReportCache.Entry cached = lisAiReportCache.get(inspectionRequestId);
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

    private String resolveResultText(InspectionResultRequest request) {
        if (request.getResultText() != null && !request.getResultText().isBlank()) {
            return request.getResultText().trim();
        }
        return LisReportStubSupport.composeResultText(
                request.getAiReportText(),
                request.getDoctorReportText()
        );
    }
}
