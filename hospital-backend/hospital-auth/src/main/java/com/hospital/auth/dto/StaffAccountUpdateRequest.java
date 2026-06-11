package com.hospital.auth.dto;

import lombok.Data;

@Data
public class StaffAccountUpdateRequest {
    private String username;
    private String password;
    /** 1 启用 0 禁用 */
    private Integer status;
}
