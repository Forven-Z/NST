package com.hospital.management.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffAccountCreateFeignRequest {
    private Long employeeId;
    private String username;
    private String password;
    private String roleType;
}
