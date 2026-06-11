package com.hospital.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmployeeWriteRequest {
    @NotBlank
    private String empNo;
    @NotBlank
    private String realName;
    private Integer gender;
    @NotNull
    private Long deptId;
    private String title;
    @NotBlank
    private String roleType;
    private String phone;
    @NotBlank
    private String username;
    private String password = "123456";
}
