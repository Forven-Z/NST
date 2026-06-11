package com.hospital.lis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InspectionResultRequest {

    @NotBlank
    private String resultText;

    private String resultAttachment;
}
