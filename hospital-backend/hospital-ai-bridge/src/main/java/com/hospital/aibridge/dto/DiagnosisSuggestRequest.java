package com.hospital.aibridge.dto;

import lombok.Data;

import java.util.List;

@Data
public class DiagnosisSuggestRequest {

    private Long registerId;
    private String symptomsSummary;
    private String readme;
    private String present;
    private String presentTreat;
    private String history;
    private String allergy;
    private String physique;
    private String diagnosis;
    private String cure;
    private String checkAdvice;
    private String inspectionAdvice;
    private List<Long> diseaseIds;
}
