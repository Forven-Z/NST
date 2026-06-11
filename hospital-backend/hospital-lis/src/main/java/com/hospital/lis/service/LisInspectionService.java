package com.hospital.lis.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.lis.dto.InspectionResultRequest;
import com.hospital.lis.repository.InspectionRequestRepository;
import com.hospital.lis.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LisInspectionService {

    private final InspectionRequestRepository inspectionRequestRepository;

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
        Map<String, Object> row = inspectionRequestRepository.findResultDetail(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));

        int currentStatus = ((Number) row.get("status")).intValue();
        if (currentStatus < InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "尚未缴费，无法查看");
        }

        Map<String, Object> result = new HashMap<>(row);
        result.put("instrumentData", "");
        result.put("aiReportText", "");
        result.put("doctorReportText", "");
        result.put("aiReportStatus", "PENDING");
        return result;
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

        inspectionRequestRepository.saveResult(
                inspectionRequestId,
                inputId,
                request.getResultText(),
                request.getResultAttachment()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("inspectionRequestId", inspectionRequestId);
        result.put("status", InspectionRequestStatus.RESULT_READY);
        result.put("resultText", request.getResultText());
        return result;
    }
}
