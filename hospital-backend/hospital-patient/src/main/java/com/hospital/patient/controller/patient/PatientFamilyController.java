package com.hospital.patient.controller.patient;

import com.hospital.common.Result;
import com.hospital.patient.dto.patient.AddFamilyMemberRequest;
import com.hospital.patient.service.PatientFamilyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/patient/family-members")
@RequiredArgsConstructor
public class PatientFamilyController {

    private final PatientFamilyService patientFamilyService;

    @GetMapping
    public Result<Map<String, Object>> list() {
        return Result.success(patientFamilyService.listMembers());
    }

    @PostMapping
    public Result<Map<String, Object>> add(@Valid @RequestBody AddFamilyMemberRequest request) {
        return Result.success(patientFamilyService.addMember(request));
    }
}
