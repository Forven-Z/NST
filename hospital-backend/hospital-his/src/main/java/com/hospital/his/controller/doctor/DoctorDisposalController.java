package com.hospital.his.controller.doctor;

import com.hospital.common.Result;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.service.DisposalOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/doctor/disposal-requests")
@RequiredArgsConstructor
public class DoctorDisposalController {

    private final DisposalOrderService disposalOrderService;

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CreateInspectionRequest request) {
        return Result.success(disposalOrderService.createDisposalOrder(request));
    }

    @GetMapping("/{id}/result")
    public Result<Map<String, Object>> getResult(@PathVariable Long id) {
        return Result.success(disposalOrderService.getDisposalResult(id));
    }
}
