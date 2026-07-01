package com.hospital.pharmacy.controller.pharmacy;

import com.hospital.common.Result;
import com.hospital.pharmacy.dto.pharmacy.CreateDrugRequest;
import com.hospital.pharmacy.dto.pharmacy.UpdateDrugRequest;
import com.hospital.pharmacy.service.PharmacyDrugService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/pharmacy/drugs")
@RequiredArgsConstructor
public class PharmacyDrugController {

    private final PharmacyDrugService pharmacyDrugService;

    @GetMapping
    public Result<Map<String, Object>> listDrugs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(pharmacyDrugService.listDrugs(keyword, includeDisabled, page, pageSize));
    }

    @PostMapping
    public Result<Map<String, Object>> createDrug(@Valid @RequestBody CreateDrugRequest request) {
        return Result.success(pharmacyDrugService.createDrug(request));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> updateDrug(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDrugRequest request) {
        return Result.success(pharmacyDrugService.updateDrug(id, request));
    }

    @PostMapping("/{id}/disable")
    public Result<Map<String, Object>> disableDrug(@PathVariable Long id) {
        return Result.success(pharmacyDrugService.disableDrug(id));
    }

    @PostMapping("/{id}/enable")
    public Result<Map<String, Object>> enableDrug(@PathVariable Long id) {
        return Result.success(pharmacyDrugService.enableDrug(id));
    }
}
