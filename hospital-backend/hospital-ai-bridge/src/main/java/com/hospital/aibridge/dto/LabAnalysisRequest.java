package com.hospital.aibridge.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LabAnalysisRequest {

    private String itemName;
    private String patientGender;
    private String patientAge;
    private String clinicalDiagnosis;
    private List<Map<String, Object>> items;
}
