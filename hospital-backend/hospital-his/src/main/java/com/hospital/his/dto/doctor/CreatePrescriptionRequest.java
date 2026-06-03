package com.hospital.his.dto.doctor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreatePrescriptionRequest {

    @NotNull
    private Long registerId;

    private String remark;

    @NotEmpty
    @Valid
    private List<PrescriptionItemRequest> items;

    @Data
    public static class PrescriptionItemRequest {

        @NotNull
        private Long drugId;

        @NotNull
        private BigDecimal quantity;

        private String usageMethod;
        private String dosage;
        private String frequency;
        private Integer days;
        private String entrust;
    }
}
