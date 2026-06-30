package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.his.dto.doctor.CreatePrescriptionRequest;
import com.hospital.his.dto.doctor.UpdatePrescriptionRequest;
import com.hospital.his.order.handler.MedicalOrderHandlerRegistry;
import com.hospital.his.order.handler.OrderCreateContext;
import com.hospital.his.order.handler.PrescriptionMedicalOrderHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final MedicalOrderHandlerRegistry medicalOrderHandlerRegistry;

    public Map<String, Object> createPrescription(CreatePrescriptionRequest request) {
        return medicalOrderHandlerRegistry.handler(BillBizType.PRESCRIPTION)
                .createOrder(OrderCreateContext.prescription(request));
    }

    public Map<String, Object> updatePrescription(Long prescriptionId, UpdatePrescriptionRequest request) {
        return prescriptionHandler().updateRejectedPrescription(prescriptionId, request);
    }

    public Map<String, Object> resubmitPrescription(Long prescriptionId) {
        return prescriptionHandler().resubmitPrescription(prescriptionId);
    }

    private PrescriptionMedicalOrderHandler prescriptionHandler() {
        return (PrescriptionMedicalOrderHandler) medicalOrderHandlerRegistry.handler(BillBizType.PRESCRIPTION);
    }
}
