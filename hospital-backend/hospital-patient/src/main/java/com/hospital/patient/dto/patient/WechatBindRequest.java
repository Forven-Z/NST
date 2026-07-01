package com.hospital.patient.dto.patient;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatBindRequest {

    @NotBlank(message = "code 不能为空")
    private String code;
}
