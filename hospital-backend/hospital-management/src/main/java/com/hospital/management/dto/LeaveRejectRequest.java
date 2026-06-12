package com.hospital.management.dto;

import lombok.Data;

@Data
public class LeaveRejectRequest {
    private String remark;
    private String adminName;
}
