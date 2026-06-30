package com.hospital.aibridge.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DoctorAiDraftRequest {

    private Long registerId;
    private String draftType;
    private DiagnosisSuggestRequest medicalRecord;
    private String clinicalResultContext;
    private List<Map<String, Object>> candidates;
}
