package com.hospital.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PatientTokenRequest {

    @NotNull(message = "patientId 不能为空")
    private Long patientId;

    private String medicalRecordNo;
}
