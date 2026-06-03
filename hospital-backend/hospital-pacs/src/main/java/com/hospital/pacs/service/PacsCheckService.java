package com.hospital.pacs.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.pacs.dto.CheckResultRequest;
import com.hospital.pacs.repository.CheckRequestRepository;
import com.hospital.pacs.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PacsCheckService {

    private final CheckRequestRepository checkRequestRepository;

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

    @Transactional
    public Map<String, Object> saveResult(Long checkRequestId, CheckResultRequest request) {
        Long inputId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> row = checkRequestRepository.findByIdForUpdate(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));

        int status = ((Number) row.get("status")).intValue();
        if (status != InspectionRequestStatus.PAID && status != InspectionRequestStatus.EXECUTED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可录入结果");
        }

        checkRequestRepository.saveResult(checkRequestId, inputId, request.getResultText(), request.getResultAttachment());

        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkRequestId);
        result.put("status", InspectionRequestStatus.RESULT_READY);
        result.put("resultText", request.getResultText());
        return result;
    }

    public Map<String, Object> imagingUploadStub() {
        Map<String, Object> result = new HashMap<>();
        result.put("stub", true);
        result.put("message", "MinIO/CNN 链路未启用，影像上传占位");
        result.put("studyStatus", "PENDING");
        return result;
    }
}
