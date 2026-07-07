package com.hospital.aibridge.dto;

import lombok.Data;

@Data
public class HeadCtImpressionRequest {

    private String findingsText;
    private String itemName;
    private String modality;
    private String bodyPart;
    private String patientGender;
    private String patientAge;
    private String clinicalDiagnosis;
}
