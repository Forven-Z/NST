package com.hospital.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PatientTokenResponse {

    private String accessToken;
    private Long expiresIn;
    private String tokenType;
}
