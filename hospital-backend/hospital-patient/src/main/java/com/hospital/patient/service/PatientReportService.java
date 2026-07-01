package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.repository.CheckRequestRepository;
import com.hospital.patient.repository.DisposalRequestRepository;
import com.hospital.patient.repository.InspectionRequestRepository;
import com.hospital.patient.repository.ImagingStudyRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.security.AuthContextHolder;
import com.hospital.patient.support.CheckReportImagingSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientReportService {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final PatientFamilyService patientFamilyService;
    private final PatientRepository patientRepository;
    private final LabReportQueryService labReportQueryService;
    private final CheckReportQueryService checkReportQueryService;
    private final DisposalRecordQueryService disposalRecordQueryService;
    private final ImagingStudyRepository imagingStudyRepository;

    public Map<String, Object> listReports(String type, Long visitPatientId) {
        Long visitId = patientFamilyService.resolveVisitPatientId(visitPatientId);
        String patientName = patientRepository.findProfileById(visitId)
                .map(p -> p.getRealName())
                .orElse("就诊人");

        List<Map<String, Object>> list = new ArrayList<>();
        if (!StringUtils.hasText(type) || "all".equals(type) || "lab".equals(type)) {
            inspectionRequestRepository.findResultsByPatient(visitId).stream()
                    .map(row -> toReportSummary(row, "lab", "检验", patientName))
                    .forEach(list::add);
        }
        if (!StringUtils.hasText(type) || "all".equals(type) || "exam".equals(type)) {
            checkRequestRepository.findResultsByPatient(visitId).stream()
                    .map(row -> toReportSummary(row, "exam", "检查", patientName))
                    .forEach(list::add);
        }
        if (!StringUtils.hasText(type) || "all".equals(type) || "disposal".equals(type)) {
            disposalRequestRepository.findResultsByPatient(visitId).stream()
                    .map(row -> toReportSummary(row, "disposal", "处置记录", patientName))
                    .forEach(list::add);
        }

        list.sort(Comparator.comparing(
                (Map<String, Object> r) -> (String) r.get("reportTimeSort"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("visitPatientId", visitId);
        result.put("pendingCount", countPendingResults(visitId));
        return result;
    }

    private int countPendingResults(Long visitId) {
        return inspectionRequestRepository.countPendingResultsByPatient(visitId)
                + checkRequestRepository.countPendingResultsByPatient(visitId)
                + disposalRequestRepository.countPendingResultsByPatient(visitId);
    }

    public Map<String, Object> getReportDetail(String type, Long requestId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        if ("lab".equals(type)) {
            Map<String, Object> row = inspectionRequestRepository.findById(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验报告不存在"));
            assertPatientCanView(operatorId, row);
            assertResultReady(row);
            Map<String, Object> labReport = labReportQueryService.getLabReportForPatient(requestId);
            labReport.put("type", "lab");
            labReport.put("typeLabel", "检验");
            labReport.put("requestId", requestId);
            labReport.put("reportName", labReport.get("reportTitle"));
            labReport.put("registerId", row.get("registerId"));
            labReport.put("patientId", row.get("patientId"));
            labReport.put("purpose", row.get("purpose"));
            labReport.put("bodyPart", row.get("bodyPart"));
            labReport.put("status", row.get("status"));
            return labReport;
        }
        if ("exam".equals(type)) {
            Map<String, Object> row = checkRequestRepository.findDetailById(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查报告不存在"));
            assertPatientCanView(operatorId, row);
            assertResultReady(row);
            Map<String, Object> checkReport = checkReportQueryService.getCheckReportForPatient(requestId);
            checkReport.put("type", "exam");
            checkReport.put("typeLabel", "检查");
            checkReport.put("requestId", requestId);
            checkReport.put("reportName", checkReport.get("reportTitle"));
            checkReport.put("registerId", row.get("registerId"));
            checkReport.put("patientId", row.get("patientId"));
            checkReport.put("purpose", row.get("purpose"));
            checkReport.put("bodyPart", row.get("bodyPart"));
            checkReport.put("status", row.get("status"));
            return checkReport;
        }
        if ("disposal".equals(type)) {
            Map<String, Object> row = disposalRequestRepository.findById(requestId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置报告不存在"));
            assertPatientCanView(operatorId, row);
            assertResultReady(row);
            Map<String, Object> record = disposalRecordQueryService.getDisposalRecordForPatient(requestId);
            record.put("type", "disposal");
            record.put("typeLabel", "处置记录");
            record.put("requestId", requestId);
            record.put("reportName", record.get("reportTitle"));
            record.put("registerId", row.get("registerId"));
            record.put("patientId", row.get("patientId"));
            record.put("purpose", row.get("purpose"));
            record.put("bodyPart", row.get("bodyPart"));
            record.put("status", row.get("status"));
            return record;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "type 须为 lab、exam 或 disposal");
    }

    private void assertPatientCanView(Long operatorId, Map<String, Object> row) {
        Long patientId = ((Number) row.get("patientId")).longValue();
        if (!patientFamilyService.canAccessVisitPatient(operatorId, patientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该报告");
        }
    }

    private void assertResultReady(Map<String, Object> row) {
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.RESULT_READY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告尚未出具");
        }
    }

    private Map<String, Object> toReportSummary(Map<String, Object> row, String type, String typeLabel,
                                                String patientName) {
        String itemName = (String) row.get("itemName");
        String resultText = (String) row.get("resultText");
        OffsetDateTime resultTime = (OffsetDateTime) row.get("resultTime");
        String reportTime = formatTime(resultTime);
        long requestId;
        if ("lab".equals(type)) {
            requestId = ((Number) row.get("inspectionRequestId")).longValue();
        } else if ("exam".equals(type)) {
            requestId = ((Number) row.get("checkRequestId")).longValue();
        } else {
            requestId = ((Number) row.get("disposalRequestId")).longValue();
        }

        Map<String, Object> item = new HashMap<>();
        item.put("id", requestId);
        item.put("requestId", requestId);
        item.put("type", type);
        item.put("typeLabel", typeLabel);
        item.put("reportName", itemName);
        item.put("patientName", patientName);
        item.put("reportTime", reportTime);
        item.put("reportTimeSort", resultTime != null ? resultTime.toString() : "");
        item.put("summary", summarize(resultText));
        item.put("registerId", row.get("registerId"));
        if ("exam".equals(type)) {
            long checkRequestId = requestId;
            boolean hasSnapshots = imagingStudyRepository.findByCheckRequestId(checkRequestId)
                    .map(study -> CheckReportImagingSupport.buildImagingSummary(checkRequestId, study, true))
                    .map(imaging -> Boolean.TRUE.equals(imaging.get("hasSnapshots")))
                    .orElse(false);
            item.put("hasSnapshots", hasSnapshots);
        } else {
            item.put("hasSnapshots", false);
        }
        return item;
    }

    private Map<String, Object> toReportDetail(Map<String, Object> row, String type, String typeLabel) {
        OffsetDateTime resultTime = (OffsetDateTime) row.get("resultTime");
        long requestId;
        if ("lab".equals(type)) {
            requestId = ((Number) row.get("inspectionRequestId")).longValue();
        } else if ("exam".equals(type)) {
            requestId = ((Number) row.get("checkRequestId")).longValue();
        } else {
            requestId = ((Number) row.get("disposalRequestId")).longValue();
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("requestId", requestId);
        detail.put("type", type);
        detail.put("typeLabel", typeLabel);
        detail.put("reportName", row.get("itemName"));
        detail.put("registerId", row.get("registerId"));
        detail.put("patientId", row.get("patientId"));
        detail.put("purpose", row.get("purpose"));
        detail.put("bodyPart", row.get("bodyPart"));
        detail.put("resultText", row.get("resultText"));
        detail.put("reportTime", formatTime(resultTime));
        detail.put("status", row.get("status"));
        return detail;
    }

    private String summarize(String resultText) {
        if (!StringUtils.hasText(resultText)) {
            return "报告已出，点击查看详情";
        }
        String trimmed = resultText.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "…";
    }

    private String formatTime(OffsetDateTime time) {
        return time != null ? time.format(DISPLAY_TIME) : "—";
    }
}
