package com.hospital.management.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CopyWeekRequest {
    @NotNull
    private Long deptId;
    @NotNull
    private LocalDate sourceWeekStart;
    @NotNull
    private LocalDate targetWeekStart;
}
