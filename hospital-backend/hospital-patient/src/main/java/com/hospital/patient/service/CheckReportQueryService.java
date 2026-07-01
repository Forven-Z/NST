package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.CheckReportComposer;
import com.hospital.patient.repository.CheckRequestRepository;
import com.hospital.patient.repository.ImagingStudyRepository;
import com.hospital.patient.support.CheckReportImagingSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CheckReportQueryService {

    private final CheckRequestRepository checkRequestRepository;
    private final ImagingStudyRepository imagingStudyRepository;

    public Map<String, Object> getCheckReportForPatient(Long checkRequestId) {
        Map<String, Object> context = checkRequestRepository.findCheckReportContext(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查报告不存在"));
        assertResultReady(context);
        String resultText = context.get("resultText") != null ? String.valueOf(context.get("resultText")) : "";
        var parsed = CheckReportComposer.parsePublishedText(resultText);
        String aiStatus = !parsed.aiReportText().isBlank() || !parsed.doctorReportText().isBlank()
                ? "READY" : "PENDING";
        return composeView(context, parsed.findingsText(), parsed.aiReportText(), parsed.doctorReportText(), aiStatus);
    }

    private Map<String, Object> composeView(
            Map<String, Object> context,
            String findings,
            String aiReportText,
            String doctorReportText,
            String aiStatus) {
        Long checkRequestId = ((Number) context.get("checkRequestId")).longValue();
        Map<String, Object> study = imagingStudyRepository.findByCheckRequestId(checkRequestId).orElse(null);
        Map<String, Object> imaging = CheckReportImagingSupport.buildImagingSummary(checkRequestId, study, true);
        return CheckReportComposer.composeView(
                context,
                findings,
                aiReportText,
                doctorReportText,
                aiStatus,
                imaging
        );
    }

    private void assertResultReady(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.RESULT_READY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检查结果尚未出具");
        }
    }
}
