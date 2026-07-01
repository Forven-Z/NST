package com.hospital.patient.controller.internal;

import com.hospital.common.Result;
import com.hospital.common.internal.CreateBillCommand;
import com.hospital.common.internal.PrescriptionBillResubmitCommand;
import com.hospital.patient.service.InternalBillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/bills")
@RequiredArgsConstructor
public class InternalBillController {

    private final InternalBillService internalBillService;

    @PostMapping("/create")
    public Result<Map<String, Object>> createBill(@Valid @RequestBody CreateBillCommand command) {
        return Result.success(internalBillService.createBill(command));
    }

    @PostMapping("/prescription-resubmit")
    public Result<Map<String, Object>> resubmitPrescriptionBill(
            @Valid @RequestBody PrescriptionBillResubmitCommand command) {
        return Result.success(internalBillService.resubmitPrescriptionBill(command));
    }
}
