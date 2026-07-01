package com.hospital.patient.client.dto;

import lombok.Data;

@Data
public class PatientTokenFeignResponse {

    private String accessToken;
    private Long expiresIn;
    private String tokenType;
}
