package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.LabReportComposer;
import com.hospital.common.support.LabReportItemTemplates;
import com.hospital.patient.repository.InspectionRequestRepository;
import com.hospital.patient.repository.InspectionResultItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LabReportQueryService {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final InspectionResultItemRepository inspectionResultItemRepository;

    public Map<String, Object> getLabReportForPatient(Long inspectionRequestId) {
        Map<String, Object> context = inspectionRequestRepository.findLabReportContext(inspectionRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验报告不存在"));
        assertResultReady(context);
        return compose(context);
    }

    private Map<String, Object> compose(Map<String, Object> context) {
        Long id = ((Number) context.get("inspectionRequestId")).longValue();
        String itemName = (String) context.get("itemName");
        List<Map<String, Object>> items = inspectionResultItemRepository.findByRequestId(id);
        if (items.isEmpty()) {
            items = LabReportItemTemplates.defaultItemsFor(itemName);
        }

        String resultText = context.get("resultText") != null ? String.valueOf(context.get("resultText")) : "";
        var parsed = LabReportComposer.parsePublishedText(resultText);
        String aiReportStatus = !parsed.aiReportText().isBlank() || !parsed.doctorReportText().isBlank()
                ? "READY" : "PENDING";

        return LabReportComposer.composeView(
                context,
                items,
                parsed.aiReportText(),
                parsed.doctorReportText(),
                aiReportStatus
        );
    }

    private void assertResultReady(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.RESULT_READY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检验结果尚未出具");
        }
    }
}
