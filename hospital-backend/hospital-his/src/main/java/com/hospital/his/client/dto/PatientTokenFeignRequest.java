package com.hospital.his.client.dto;

import lombok.Data;

@Data
public class PatientTokenFeignRequest {

    private Long patientId;
    private String medicalRecordNo;
}
