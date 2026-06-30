package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.order.handler.MedicalOrderHandlerRegistry;
import com.hospital.his.order.handler.OrderCreateContext;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class InspectionOrderService {

    private final MedicalOrderHandlerRegistry medicalOrderHandlerRegistry;
    private final LabReportQueryService labReportQueryService;

    public Map<String, Object> createInspectionOrder(CreateInspectionRequest request) {
        return medicalOrderHandlerRegistry.handler(BillBizType.INSPECTION)
                .createOrder(OrderCreateContext.medTech(request));
    }

    public Map<String, Object> getInspectionResult(Long inspectionRequestId) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        return labReportQueryService.getLabReportForDoctor(inspectionRequestId, doctorId);
    }
}
