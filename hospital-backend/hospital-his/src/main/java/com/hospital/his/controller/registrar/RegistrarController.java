package com.hospital.his.controller.registrar;

import com.hospital.common.Result;
import com.hospital.his.dto.registrar.CancelRegisterRequest;
import com.hospital.his.dto.registrar.RefundRequest;
import com.hospital.his.dto.registrar.WindowChargeRequest;
import com.hospital.his.dto.registrar.WindowRegisterRequest;
import com.hospital.his.service.RefundService;
import com.hospital.his.service.RegisterCancelService;
import com.hospital.his.service.RegistrarChargeService;
import com.hospital.his.service.RegistrarQueryService;
import com.hospital.his.service.RegistrarRegisterService;
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
@RequestMapping("/api/v1/registrar")
@RequiredArgsConstructor
public class RegistrarController {

    private final RefundService refundService;
    private final RegisterCancelService registerCancelService;
    private final RegistrarQueryService registrarQueryService;
    private final RegistrarRegisterService registrarRegisterService;
    private final RegistrarChargeService registrarChargeService;

    @PostMapping("/registers")
    public Result<Map<String, Object>> windowRegister(@Valid @RequestBody WindowRegisterRequest request) {
        return Result.success(registrarRegisterService.windowRegister(request));
    }

    @PostMapping("/charges")
    public Result<Map<String, Object>> windowCharge(@Valid @RequestBody WindowChargeRequest request) {
        return Result.success(registrarChargeService.windowCharge(request));
    }

    @GetMapping("/departments")
    public Result<Map<String, Object>> listDepartments() {
        return Result.success(registrarQueryService.listOutpatientDepartments());
    }

    @GetMapping("/settle-categories")
    public Result<Map<String, Object>> listSettleCategories() {
        return Result.success(registrarQueryService.listSettleCategories());
    }

    @GetMapping("/doctors")
    public Result<Map<String, Object>> listDoctors(@RequestParam Long deptId) {
        return Result.success(registrarQueryService.listDoctorsByDept(deptId));
    }

    @GetMapping("/schedules")
    public Result<Map<String, Object>> listSchedules(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long registLevelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        return Result.success(registrarQueryService.listSchedules(deptId, employeeId, registLevelId, workDate));
    }

    @GetMapping("/patients/bills")
    public Result<Map<String, Object>> listPatientBillsByQuery(
            @RequestParam(required = false) String medicalRecordNo,
            @RequestParam(required = false) String idCard,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Integer status) {
        return Result.success(registrarQueryService.listBillsByQuery(
                medicalRecordNo, idCard, realName, patientId, status));
    }

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
