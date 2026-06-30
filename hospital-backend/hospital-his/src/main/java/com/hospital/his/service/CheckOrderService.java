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
public class CheckOrderService {

    private final MedicalOrderHandlerRegistry medicalOrderHandlerRegistry;
    private final CheckReportQueryService checkReportQueryService;

    public Map<String, Object> createCheckOrder(CreateInspectionRequest request) {
        return medicalOrderHandlerRegistry.handler(BillBizType.CHECK)
                .createOrder(OrderCreateContext.medTech(request));
    }

    public Map<String, Object> getCheckResult(Long checkRequestId) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        return checkReportQueryService.getCheckReportForDoctor(checkRequestId, doctorId);
    }
}
