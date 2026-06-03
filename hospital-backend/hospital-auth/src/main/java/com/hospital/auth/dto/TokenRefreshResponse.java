package com.hospital.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenRefreshResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType;
}
