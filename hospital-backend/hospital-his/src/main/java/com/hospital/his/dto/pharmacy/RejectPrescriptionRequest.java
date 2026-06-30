package com.hospital.his.dto.pharmacy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectPrescriptionRequest {

    @NotBlank(message = "请填写拒绝原因")
    @Size(max = 256)
    private String reason;
}
