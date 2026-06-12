package com.hospital.aibridge.dto;

import com.hospital.aibridge.domain.TriageStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriageChatResponse {

    private String sessionId;
    private String reply;
    private TriageStage stage;
    private Boolean needMoreInfo;
    private Boolean needRegister;
    private Boolean emergency;
    private String emergencyReason;
    private String summary;
    @Builder.Default
    private List<String> askedQuestions = new ArrayList<>();
    @Builder.Default
    private List<String> quickReplies = new ArrayList<>();
    @Builder.Default
    private List<DepartmentRecommendation> recommendedDepartments = new ArrayList<>();
    private String safetyNotice;
}
