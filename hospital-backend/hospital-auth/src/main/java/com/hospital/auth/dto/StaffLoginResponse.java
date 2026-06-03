package com.hospital.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StaffLoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Long userId;
    private Long employeeId;
    private String realName;
    private List<String> roles;
    private Long deptId;
    private String deptName;
}
