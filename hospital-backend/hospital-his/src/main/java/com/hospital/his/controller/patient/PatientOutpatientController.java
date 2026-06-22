package com.hospital.his.controller.patient;

import com.hospital.common.Result;
import com.hospital.his.dto.patient.CreateRegisterRequest;
import com.hospital.his.dto.patient.MockPaymentRequest;
import com.hospital.his.dto.registrar.CancelRegisterRequest;
import com.hospital.his.dto.registrar.RefundRequest;
import com.hospital.his.service.FinancialQueryService;
import com.hospital.his.service.PatientFamilyService;
import com.hospital.his.service.PaymentService;
import com.hospital.his.service.RefundService;
import com.hospital.his.service.RegisterCancelService;
import com.hospital.his.service.RegisterService;
import com.hospital.his.repository.DepartmentRepository;
import com.hospital.his.service.PatientRegisterQueryService;
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
    private final PatientRegisterQueryService patientRegisterQueryService;
    private final DepartmentRepository departmentRepository;
    private final com.hospital.his.service.PatientReportService patientReportService;
    private final FinancialQueryService financialQueryService;
    private final PatientFamilyService patientFamilyService;

    @GetMapping("/departments")
    public Result<Map<String, Object>> listDepartments() {
        return Result.success(Map.of("list", departmentRepository.listOutpatientDepartments()));
    }

    @GetMapping("/registers")
    public Result<Map<String, Object>> listRegisters(
            @RequestParam(required = false) Integer visitState,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long visitPatientId,
            @RequestParam(required = false) Long patientId) {
        Long visitId = visitPatientId != null ? visitPatientId : patientId;
        return Result.success(patientRegisterQueryService.listRegisters(visitState, page, pageSize, visitId));
    }

    @GetMapping("/registers/{registerId}")
    public Result<Map<String, Object>> getRegister(@PathVariable Long registerId) {
        return Result.success(patientRegisterQueryService.getRegister(registerId));
    }

    @GetMapping("/registers/{registerId}/queue-status")
    public Result<Map<String, Object>> queueStatus(@PathVariable Long registerId) {
        return Result.success(patientRegisterQueryService.getQueueStatus(registerId));
    }

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
    public Result<Map<String, Object>> listBills(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long registerId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long visitPatientId,
            @RequestParam(required = false) Long patientId) {
        Long visitId = visitPatientId != null ? visitPatientId : patientId;
        return Result.success(paymentService.listBills(visitId, status, registerId, scope));
    }

    @GetMapping("/payments")
    public Result<Map<String, Object>> listPayments(
            @RequestParam(required = false) Long registerId,
            @RequestParam(required = false) Long visitPatientId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long visitId = patientFamilyService.resolveVisitPatientId(
                visitPatientId != null ? visitPatientId : patientId);
        return Result.success(financialQueryService.listPayments(visitId, registerId, page, pageSize));
    }

    @GetMapping("/payments/{paymentId}")
    public Result<Map<String, Object>> getPayment(
            @PathVariable Long paymentId,
            @RequestParam(required = false) Long visitPatientId,
            @RequestParam(required = false) Long patientId) {
        Long visitId = patientFamilyService.resolveVisitPatientId(
                visitPatientId != null ? visitPatientId : patientId);
        return Result.success(financialQueryService.getPaymentDetail(visitId, paymentId));
    }

    @GetMapping("/refunds")
    public Result<Map<String, Object>> listRefunds(
            @RequestParam(required = false) Long registerId,
            @RequestParam(required = false) Long visitPatientId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long visitId = patientFamilyService.resolveVisitPatientId(
                visitPatientId != null ? visitPatientId : patientId);
        return Result.success(financialQueryService.listRefunds(visitId, registerId, page, pageSize));
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

    @GetMapping("/reports")
    public Result<Map<String, Object>> listReports(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long visitPatientId,
            @RequestParam(required = false) Long patientId) {
        Long visitId = visitPatientId != null ? visitPatientId : patientId;
        return Result.success(patientReportService.listReports(type, visitId));
    }

    @GetMapping("/reports/{type}/{requestId}")
    public Result<Map<String, Object>> getReportDetail(
            @PathVariable String type,
            @PathVariable Long requestId) {
        return Result.success(patientReportService.getReportDetail(type, requestId));
    }
}
