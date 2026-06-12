package com.hospital.aibridge.service;

import com.hospital.aibridge.dto.DepartmentRecommendation;
import com.hospital.aibridge.dto.TriageChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TriageResponseSanitizer {

    private static final List<String> DIAGNOSIS_PREFIXES = List.of("你患有", "您患有", "可以确诊为", "确诊为", "诊断为");
    private static final List<String> MEDICATION_PREFIXES = List.of("建议服用", "可以服用", "口服", "剂量为", "用量为");
    private static final List<String> AVOID_CARE_PREFIXES = List.of("不用去医院", "无需就医", "不需要就医", "不用挂号");

    public TriageChatResponse sanitize(TriageChatResponse response) {
        response.setReply(sanitizeText(response.getReply()));
        response.setSummary(sanitizeText(response.getSummary()));
        response.setEmergencyReason(sanitizeText(response.getEmergencyReason()));
        if (response.getAskedQuestions() != null) {
            response.setAskedQuestions(response.getAskedQuestions().stream().map(this::sanitizeText).toList());
        }
        if (response.getRecommendedDepartments() != null) {
            for (DepartmentRecommendation recommendation : response.getRecommendedDepartments()) {
                recommendation.setReason(sanitizeText(recommendation.getReason()));
                recommendation.setNextAction(sanitizeText(recommendation.getNextAction()));
            }
        }
        return response;
    }

    private String sanitizeText(String text) {
        if (text == null) {
            return null;
        }
        String value = text;
        for (String prefix : DIAGNOSIS_PREFIXES) {
            value = value.replace(prefix, "症状可能与相关健康问题有关，建议由医生进一步评估，");
        }
        for (String prefix : MEDICATION_PREFIXES) {
            value = value.replace(prefix, "请勿自行用药，建议由医生评估后决定是否用药，");
        }
        for (String prefix : AVOID_CARE_PREFIXES) {
            value = value.replace(prefix, "如症状持续或加重，建议及时就医");
        }
        return value;
    }
}
