package com.hospital.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeaveRequestWriteRequest {
    @NotNull
    private Long employeeId;
    @NotBlank
    private String reason;
}
