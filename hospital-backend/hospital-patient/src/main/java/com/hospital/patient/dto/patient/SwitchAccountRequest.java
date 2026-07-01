package com.hospital.patient.dto.patient;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SwitchAccountRequest {

    @NotNull(message = "targetPatientId 不能为空")
    private Long targetPatientId;
}
