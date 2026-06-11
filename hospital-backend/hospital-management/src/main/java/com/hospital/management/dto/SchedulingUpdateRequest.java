package com.hospital.management.dto;

import lombok.Data;

@Data
public class SchedulingUpdateRequest {
    private Long employeeId;
    private Integer totalQuota;
    /** 2 作废 */
    private Integer publishStatus;
}
