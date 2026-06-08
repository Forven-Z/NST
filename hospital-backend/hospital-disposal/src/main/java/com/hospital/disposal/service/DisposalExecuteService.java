package com.hospital.disposal.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
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

        disposalRequestRepository.saveResult(
                disposalRequestId,
                inputId,
                request.getResultText(),
                request.getResultAttachment()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("disposalRequestId", disposalRequestId);
        result.put("status", InspectionRequestStatus.RESULT_READY);
        result.put("resultText", request.getResultText());
        return result;
    }
}
