package com.hospital.his.dto.patient;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MockPaymentRequest {

    @NotEmpty(message = "billIds 不能为空")
    private List<Long> billIds;
}
