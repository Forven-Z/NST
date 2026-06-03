package com.hospital.his.controller.doctor;

import com.hospital.common.Result;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.service.InspectionOrderService;
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
@RequestMapping("/api/v1/doctor/inspection-requests")
@RequiredArgsConstructor
public class DoctorInspectionController {

    private final InspectionOrderService inspectionOrderService;

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CreateInspectionRequest request) {
        return Result.success(inspectionOrderService.createInspectionOrder(request));
    }

    @GetMapping("/{id}/result")
    public Result<Map<String, Object>> getResult(@PathVariable Long id) {
        return Result.success(inspectionOrderService.getInspectionResult(id));
    }
}
