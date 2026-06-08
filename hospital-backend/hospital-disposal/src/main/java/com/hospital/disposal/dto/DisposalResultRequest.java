package com.hospital.disposal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisposalResultRequest {

    @NotBlank
    private String resultText;

    private String resultAttachment;
}
