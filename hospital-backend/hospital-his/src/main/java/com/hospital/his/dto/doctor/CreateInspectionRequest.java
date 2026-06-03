package com.hospital.his.dto.doctor;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateInspectionRequest {

    @NotNull
    private Long registerId;

    @NotNull
    private Long medicalTechnologyId;

    private String purpose;
    private String bodyPart;
    private String remark;
}
