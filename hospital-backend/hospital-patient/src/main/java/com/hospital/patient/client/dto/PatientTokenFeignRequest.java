package com.hospital.patient.client.dto;

import lombok.Data;

@Data
public class PatientTokenFeignRequest {

    private Long patientId;
    private String medicalRecordNo;
}
