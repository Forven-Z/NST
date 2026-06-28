package com.hospital.management.dto;

import lombok.Data;

@Data
public class TemplateSlotItem {
    private Integer weekday;
    private Integer noonType;
    private Long registLevelId;
    private Integer totalQuota;
    private Boolean enabled;
}
