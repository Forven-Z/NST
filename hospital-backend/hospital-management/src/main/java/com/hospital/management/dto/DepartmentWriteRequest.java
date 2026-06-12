package com.hospital.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepartmentWriteRequest {
    private String deptCode;
    @NotBlank
    private String deptName;
    @NotNull
    private Integer deptType;
    private Integer sortNo = 10;
}
