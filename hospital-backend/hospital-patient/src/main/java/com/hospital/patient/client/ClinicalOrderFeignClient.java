package com.hospital.patient.client;

import com.hospital.common.Result;
import com.hospital.common.internal.OrderBizCommand;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ClinicalOrderFeignClient.SERVICE_NAME)
public interface ClinicalOrderFeignClient {

    String SERVICE_NAME = "hospital-his";

    @PostMapping("/internal/orders/on-bill-paid")
    Result<Void> onBillPaid(@RequestBody OrderBizCommand command);

    @PostMapping("/internal/orders/on-refund")
    Result<Void> onRefund(@RequestBody OrderBizCommand command);

    @PostMapping("/internal/orders/assert-refundable")
    Result<Void> assertRefundable(@RequestBody OrderBizCommand command);
}
