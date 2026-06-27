package com.hospital.his.controller.doctor;

import com.hospital.common.Result;
import com.hospital.his.dto.doctor.CreatePrescriptionRequest;
import com.hospital.his.dto.doctor.UpdatePrescriptionRequest;
import com.hospital.his.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/doctor/prescriptions")
@RequiredArgsConstructor
public class DoctorPrescriptionController {

    private final PrescriptionService prescriptionService;

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CreatePrescriptionRequest request) {
        return Result.success(prescriptionService.createPrescription(request));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id,
            @Valid @RequestBody UpdatePrescriptionRequest request) {
        return Result.success(prescriptionService.updatePrescription(id, request));
    }

    @PostMapping("/{id}/resubmit")
    public Result<Map<String, Object>> resubmit(@PathVariable Long id) {
        return Result.success(prescriptionService.resubmitPrescription(id));
    }
}
