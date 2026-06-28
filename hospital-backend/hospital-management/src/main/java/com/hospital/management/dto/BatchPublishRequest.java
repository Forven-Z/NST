package com.hospital.management.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BatchPublishRequest {
    private Long deptId;
    private LocalDate weekStart;
}
