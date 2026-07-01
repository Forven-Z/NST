package com.hospital.his.controller.internal;

import com.hospital.common.Result;
import com.hospital.common.internal.OrderBizCommand;
import com.hospital.his.order.handler.MedicalOrderHandlerRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final MedicalOrderHandlerRegistry medicalOrderHandlerRegistry;

    @PostMapping("/on-bill-paid")
    public Result<Void> onBillPaid(@Valid @RequestBody OrderBizCommand command) {
        medicalOrderHandlerRegistry.handler(command.bizType()).onBillPaid(command.bizId());
        return Result.success(null);
    }

    @PostMapping("/on-refund")
    public Result<Void> onRefund(@Valid @RequestBody OrderBizCommand command) {
        medicalOrderHandlerRegistry.handler(command.bizType()).onRefund(command.bizId());
        return Result.success(null);
    }

    @PostMapping("/assert-refundable")
    public Result<Void> assertRefundable(@Valid @RequestBody OrderBizCommand command) {
        medicalOrderHandlerRegistry.handler(command.bizType()).assertBillRefundable(command.bizId());
        return Result.success(null);
    }
}
