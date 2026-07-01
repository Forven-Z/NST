package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.DisposalRecordComposer;
import com.hospital.patient.repository.DisposalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisposalRecordQueryService {

    private final DisposalRequestRepository disposalRequestRepository;

    public Map<String, Object> getDisposalRecordForPatient(Long disposalRequestId) {
        Map<String, Object> context = disposalRequestRepository.findDisposalRecordContext(disposalRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置记录不存在"));
        assertResultReady(context);
        return DisposalRecordComposer.composeView(context, null, null);
    }

    private void assertResultReady(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.RESULT_READY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "处置记录尚未出具");
        }
    }
}
