package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.CheckRequestRepository;
import com.hospital.his.repository.DisposalRequestRepository;
import com.hospital.his.repository.InspectionRequestRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 医生查阅患者既往就诊中的医技/检查/处置报告（不限于本医生开立）。
 */
@Service
@RequiredArgsConstructor
public class DoctorPatientVisitOrderResultService {

    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final LabReportQueryService labReportQueryService;
    private final CheckReportQueryService checkReportQueryService;
    private final DisposalRecordQueryService disposalRecordQueryService;

    public Map<String, Object> getOrderResult(Long patientId, String kind, Long requestId) {
        assertDoctorStaff();
        switch (kind) {
            case "inspection" -> {
                assertPatientOwnsInspection(patientId, requestId);
                return labReportQueryService.getLabReportForStaffReadonly(requestId);
            }
            case "check" -> {
                assertPatientOwnsCheck(patientId, requestId);
                return checkReportQueryService.getCheckReportForStaffReadonly(requestId);
            }
            case "disposal" -> {
                assertPatientOwnsDisposal(patientId, requestId);
                return disposalRecordQueryService.getDisposalRecordForStaffReadonly(requestId);
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的结果类型: " + kind);
        }
    }

    private void assertPatientOwnsInspection(Long patientId, Long requestId) {
        Map<String, Object> row = inspectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));
        assertPatientMatch(patientId, row);
    }

    private void assertPatientOwnsCheck(Long patientId, Long requestId) {
        Map<String, Object> row = checkRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));
        assertPatientMatch(patientId, row);
    }

    private void assertPatientOwnsDisposal(Long patientId, Long requestId) {
        Map<String, Object> row = disposalRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));
        assertPatientMatch(patientId, row);
    }

    private void assertPatientMatch(Long patientId, Map<String, Object> row) {
        Long owner = ((Number) row.get("patientId")).longValue();
        if (!patientId.equals(owner)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该报告");
        }
    }

    private void assertDoctorStaff() {
        if (AuthContextHolder.require().getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }
    }
}
