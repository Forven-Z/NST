package com.hospital.management.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BatchUpsertChangeItem {
    private Long schedulingId;
    private Long employeeId;
    private LocalDate workDate;
    private Integer noonType;
    private Long registLevelId;
    private Integer totalQuota;
    private Boolean clear;
}
