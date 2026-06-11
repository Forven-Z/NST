package com.hospital.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StaffAccountCreateRequest {
    @NotNull
    private Long employeeId;
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    private String roleType;
}
