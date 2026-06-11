package com.hospital.management.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SchedulingWriteRequest {
    @NotNull
    private Long employeeId;
    @NotNull
    private Long registLevelId;
    @NotNull
    private LocalDate workDate;
    @NotNull
    private Integer noonType;
    @NotNull
    private Integer totalQuota;
}
