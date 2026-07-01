package com.hospital.his.client;

import com.hospital.common.Result;
import com.hospital.common.internal.CreateBillCommand;
import com.hospital.common.internal.PrescriptionBillResubmitCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = PatientBillFeignClient.SERVICE_NAME)
public interface PatientBillFeignClient {

    String SERVICE_NAME = "hospital-patient";

    @PostMapping("/internal/bills/create")
    Result<Map<String, Object>> createBill(@RequestBody CreateBillCommand command);

    @PostMapping("/internal/bills/prescription-resubmit")
    Result<Map<String, Object>> resubmitPrescriptionBill(@RequestBody PrescriptionBillResubmitCommand command);
}
