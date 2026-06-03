package com.hospital.his.dto.doctor;

import lombok.Data;

@Data
public class MedicalRecordSaveRequest {

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
}
