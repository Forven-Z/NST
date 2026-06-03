package com.hospital.his.controller.registrar;

import com.hospital.common.Result;
import com.hospital.his.dto.registrar.CancelRegisterRequest;
import com.hospital.his.dto.registrar.RefundRequest;
import com.hospital.his.service.RefundService;
import com.hospital.his.service.RegisterCancelService;
import com.hospital.his.service.RegistrarQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/registrar")
@RequiredArgsConstructor
public class RegistrarController {

    private final RefundService refundService;
    private final RegisterCancelService registerCancelService;
    private final RegistrarQueryService registrarQueryService;

    @GetMapping("/patients/{medicalRecordNo}/bills")
    public Result<Map<String, Object>> listPatientBills(
            @PathVariable String medicalRecordNo,
            @RequestParam(required = false) Integer status) {
        return Result.success(registrarQueryService.listBillsByMedicalRecordNo(medicalRecordNo, status));
    }

    @PostMapping("/refunds")
    public Result<Map<String, Object>> refund(@Valid @RequestBody RefundRequest request) {
        return Result.success(refundService.refundByRegistrar(
                request.getBillId(), request.getReason()));
    }

    @PostMapping("/registers/{registerId}/cancel")
    public Result<Map<String, Object>> cancelRegister(
            @PathVariable Long registerId,
            @RequestBody(required = false) CancelRegisterRequest request) {
        String reason = request != null ? request.getReason() : null;
        return Result.success(registerCancelService.cancelByRegistrar(registerId, reason));
    }
}
