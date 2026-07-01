package com.hospital.patient.controller.internal;

import com.hospital.common.Result;
import com.hospital.common.internal.PrescriptionPharmacyRejectCommand;
import com.hospital.patient.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/refunds")
@RequiredArgsConstructor
public class InternalRefundController {

    private final RefundService refundService;

    @PostMapping("/prescription-pharmacy-reject")
    public Result<Map<String, Object>> prescriptionPharmacyReject(
            @Valid @RequestBody PrescriptionPharmacyRejectCommand command) {
        return Result.success(refundService.refundPrescriptionBillForPharmacyReject(
                command.prescriptionId(), command.reason(), command.pharmacistId()));
    }
}
