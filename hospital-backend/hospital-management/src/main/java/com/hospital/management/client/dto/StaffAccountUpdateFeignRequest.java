package com.hospital.management.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffAccountUpdateFeignRequest {
    private String username;
    private String password;
    private Integer status;
}
