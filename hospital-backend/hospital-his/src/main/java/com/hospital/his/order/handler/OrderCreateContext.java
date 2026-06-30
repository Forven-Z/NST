package com.hospital.his.order.handler;

import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.dto.doctor.CreatePrescriptionRequest;

/**
 * 开单上下文：医技与处方共用入口，由具体 Handler 校验并消费对应字段。
 */
public final class OrderCreateContext {

    private final CreateInspectionRequest medTechRequest;
    private final CreatePrescriptionRequest prescriptionRequest;

    private OrderCreateContext(CreateInspectionRequest medTechRequest, CreatePrescriptionRequest prescriptionRequest) {
        this.medTechRequest = medTechRequest;
        this.prescriptionRequest = prescriptionRequest;
    }

    public static OrderCreateContext medTech(CreateInspectionRequest request) {
        return new OrderCreateContext(request, null);
    }

    public static OrderCreateContext prescription(CreatePrescriptionRequest request) {
        return new OrderCreateContext(null, request);
    }

    public CreateInspectionRequest medTechRequest() {
        return medTechRequest;
    }

    public CreatePrescriptionRequest prescriptionRequest() {
        return prescriptionRequest;
    }
}
