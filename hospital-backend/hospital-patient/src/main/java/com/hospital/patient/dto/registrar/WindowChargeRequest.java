package com.hospital.patient.dto.registrar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WindowChargeRequest {

    @NotEmpty(message = "billIds 不能为空")
    private List<Long> billIds;

    @NotBlank(message = "payChannel 不能为空")
    private String payChannel;
}
