package com.hospital.his.controller.patient;

import com.hospital.common.Result;
import com.hospital.his.dto.patient.CreateRegisterRequest;
import com.hospital.his.dto.patient.MockPaymentRequest;
import com.hospital.his.dto.registrar.CancelRegisterRequest;
import com.hospital.his.dto.registrar.RefundRequest;
import com.hospital.his.service.PaymentService;
import com.hospital.his.service.RefundService;
import com.hospital.his.service.RegisterCancelService;
import com.hospital.his.service.RegisterService;
import com.hospital.his.service.SchedulingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
public class PatientOutpatientController {

    private final SchedulingService schedulingService;
    private final RegisterService registerService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final RegisterCancelService registerCancelService;
    private final com.hospital.his.service.DoctorMedicalRecordService doctorMedicalRecordService;

    @GetMapping("/schedules")
    public Result<Map<String, Object>> listSchedules(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam(required = false) Integer noonType,
            @RequestParam(required = false) Long registLevelId) {
        return Result.success(schedulingService.listSchedules(deptId, workDate, noonType, registLevelId));
    }

    @PostMapping("/registers")
    public Result<Map<String, Object>> createRegister(@Valid @RequestBody CreateRegisterRequest request) {
        return Result.success(registerService.createRegister(request));
    }

    @GetMapping("/bills")
    public Result<Map<String, Object>> listPendingBills() {
        return Result.success(paymentService.listPendingBills());
    }

    @PostMapping("/payments")
    public Result<Map<String, Object>> mockPayment(@Valid @RequestBody MockPaymentRequest request) {
        return Result.success(paymentService.mockPay(request));
    }

    @PostMapping("/refunds")
    public Result<Map<String, Object>> refund(@Valid @RequestBody RefundRequest request) {
        return Result.success(refundService.refundByPatient(request.getBillId(), request.getReason()));
    }

    @PostMapping("/registers/{registerId}/cancel")
    public Result<Map<String, Object>> cancelRegister(
            @PathVariable Long registerId,
            @RequestBody(required = false) CancelRegisterRequest request) {
        String reason = request != null ? request.getReason() : null;
        return Result.success(registerCancelService.cancelByPatient(registerId, reason));
    }

    @GetMapping("/medical-records/{registerId}")
    public Result<Map<String, Object>> getMedicalRecord(@PathVariable Long registerId) {
        return Result.success(doctorMedicalRecordService.getPatientMedicalRecord(registerId));
    }
}
