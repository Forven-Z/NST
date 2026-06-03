package com.hospital.his.controller.patient;

import com.hospital.common.Result;
import com.hospital.his.dto.patient.PatientProfileResponse;
import com.hospital.his.dto.patient.PatientProfileUpdateRequest;
import com.hospital.his.service.PatientProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
public class PatientProfileController {

    private final PatientProfileService patientProfileService;

    @GetMapping("/profile")
    public Result<PatientProfileResponse> getProfile() {
        return Result.success(patientProfileService.getProfile());
    }

    @PutMapping("/profile")
    public Result<PatientProfileResponse> updateProfile(@RequestBody PatientProfileUpdateRequest request) {
        return Result.success(patientProfileService.updateProfile(request));
    }
}
