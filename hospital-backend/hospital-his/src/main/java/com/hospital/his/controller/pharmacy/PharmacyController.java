package com.hospital.his.controller.pharmacy;

import com.hospital.common.Result;
import com.hospital.his.dto.pharmacy.RejectPrescriptionRequest;
import com.hospital.his.service.PharmacyService;
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
@RequestMapping("/api/v1/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping("/pending")
    public Result<Map<String, Object>> pending(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(pharmacyService.listPending(status, page, pageSize));
    }

    @GetMapping("/prescriptions/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(pharmacyService.getPrescriptionDetail(id));
    }

    @PostMapping("/prescriptions/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id,
            @Valid @RequestBody RejectPrescriptionRequest request) {
        return Result.success(pharmacyService.rejectDispense(id, request));
    }

    @PostMapping("/prescriptions/{id}/dispense")
    public Result<Map<String, Object>> dispense(@PathVariable Long id) {
        return Result.success(pharmacyService.dispense(id));
    }

    @PostMapping("/prescriptions/{id}/return-drug")
    public Result<Map<String, Object>> returnDrug(@PathVariable Long id) {
        return Result.success(pharmacyService.returnDrug(id));
    }
}
