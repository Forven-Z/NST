package com.hospital.aibridge.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiTriageResult {

    private String reply;
    private Boolean needMoreInfo;
    private Boolean needRegister;
    private Boolean emergency;
    private String emergencyReason;
    private String summary;
    private List<String> questions = new ArrayList<>();
    private List<AiDepartment> departments = new ArrayList<>();

    @Data
    public static class AiDepartment {
        private String name;
        private Double confidence;
        private String reason;
    }
}
