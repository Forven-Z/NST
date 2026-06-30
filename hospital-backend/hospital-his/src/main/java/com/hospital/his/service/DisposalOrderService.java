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
public class DisposalOrderService {

    private final MedicalOrderHandlerRegistry medicalOrderHandlerRegistry;
    private final DisposalRecordQueryService disposalRecordQueryService;

    public Map<String, Object> createDisposalOrder(CreateInspectionRequest request) {
        return medicalOrderHandlerRegistry.handler(BillBizType.DISPOSAL)
                .createOrder(OrderCreateContext.medTech(request));
    }

    public Map<String, Object> getDisposalResult(Long disposalRequestId) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        return disposalRecordQueryService.getDisposalRecordForDoctor(disposalRequestId, doctorId);
    }
}
