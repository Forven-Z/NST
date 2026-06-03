package com.hospital.his.client.dto;

import lombok.Data;

@Data
public class PatientTokenFeignResponse {

    private String accessToken;
    private Long expiresIn;
    private String tokenType;
}
