package com.hospital.pharmacy.client;

import com.hospital.common.Result;
import com.hospital.common.internal.PrescriptionPharmacyRejectCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = PatientRefundFeignClient.SERVICE_NAME)
public interface PatientRefundFeignClient {

    String SERVICE_NAME = "hospital-patient";

    @PostMapping("/internal/refunds/prescription-pharmacy-reject")
    Result<Map<String, Object>> prescriptionPharmacyReject(@RequestBody PrescriptionPharmacyRejectCommand command);
}
