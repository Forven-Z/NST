package com.hospital.management.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SchedulingAiSuggestRequest {
    private Long deptId;
    private LocalDate weekStart;
    private String mode;
    private String rulesText;
}
