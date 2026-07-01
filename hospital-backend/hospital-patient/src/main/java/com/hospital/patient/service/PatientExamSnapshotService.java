package com.hospital.patient.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.client.PacsImagingBridge;
import com.hospital.patient.repository.CheckRequestRepository;
import com.hospital.patient.security.AuthContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PatientExamSnapshotService {

    private static final Set<String> ALLOWED_PLANES = Set.of("axial", "coronal", "sagittal");

    private final CheckRequestRepository checkRequestRepository;
    private final PatientFamilyService patientFamilyService;
    private final PacsImagingBridge pacsImagingBridge;

    public void streamSnapshot(Long checkRequestId, String plane, HttpServletResponse response) throws Exception {
        assertAllowedPlane(plane);
        String normalizedPlane = plane.toLowerCase();

        Long operatorId = AuthContextHolder.require().getPatientId();
        Map<String, Object> row = checkRequestRepository.findDetailById(checkRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查报告不存在"));

        Long patientId = ((Number) row.get("patientId")).longValue();
        if (!patientFamilyService.canAccessVisitPatient(operatorId, patientId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该报告影像");
        }

        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.RESULT_READY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检查结果尚未出具");
        }

        byte[] png = pacsImagingBridge.fetchReportSnapshot(checkRequestId, normalizedPlane);
        response.setContentType("image/png");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + normalizedPlane + ".png\"");
        StreamUtils.copy(png, response.getOutputStream());
    }

    private void assertAllowedPlane(String plane) {
        if (plane == null || !ALLOWED_PLANES.contains(plane.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "plane 须为 axial、coronal 或 sagittal");
        }
    }
}
