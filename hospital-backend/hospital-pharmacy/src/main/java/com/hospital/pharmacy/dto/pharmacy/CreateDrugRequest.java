package com.hospital.pharmacy.dto.pharmacy;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDrugRequest {

    @NotBlank
    private String drugName;

    @NotNull
    @DecimalMin(value = "0.01", message = "零售价须大于 0")
    private BigDecimal retailPrice;

    @NotNull
    @Min(0)
    private Integer stockQty;

    private String drugFormat;
    private String drugDosage;
    private String drugType;
    private String unit;
}
