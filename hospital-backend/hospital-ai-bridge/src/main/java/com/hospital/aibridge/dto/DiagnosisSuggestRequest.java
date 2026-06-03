package com.hospital.aibridge.dto;

import lombok.Data;

@Data
public class DiagnosisSuggestRequest {

    private Long registerId;
    private String symptomsSummary;
}
