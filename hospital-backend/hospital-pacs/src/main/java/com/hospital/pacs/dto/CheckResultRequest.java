package com.hospital.pacs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckResultRequest {

    @NotBlank
    private String resultText;

    private String resultAttachment;
}
