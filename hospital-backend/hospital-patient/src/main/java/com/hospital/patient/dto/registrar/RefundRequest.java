package com.hospital.patient.dto.registrar;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RefundRequest {

    @NotNull
    private Long billId;

    private String reason;
}
