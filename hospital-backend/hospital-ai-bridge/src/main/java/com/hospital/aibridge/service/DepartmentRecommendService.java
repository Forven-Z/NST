package com.hospital.aibridge.service;

import com.hospital.aibridge.dto.AiTriageResult;
import com.hospital.aibridge.dto.DepartmentRecommendation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DepartmentRecommendService {

    private final Map<String, DepartmentRecommendation> availableDepartments = new LinkedHashMap<>();

    public DepartmentRecommendService() {
        availableDepartments.put("内科", new DepartmentRecommendation(1L, "INTERNAL", "内科", null, 0.70,
                "当前系统已配置内科，可作为常见症状首诊科室。",
                "可点击去挂号，选择该科室号源。"));
    }

    public List<DepartmentRecommendation> recommendByRules(String text) {
        String value = StringUtils.hasText(text) ? text.toLowerCase(Locale.ROOT) : "";
        List<DepartmentRecommendation> result = new ArrayList<>();
        if (containsAny(value, "头痛", "头晕", "恶心", "发热", "咳嗽", "咽痛", "腹痛", "腹泻", "乏力", "心慌")) {
            result.add(copyInternal("内科", 0.78, "根据症状描述，建议先到内科进行初诊评估。"));
        } else {
            result.add(copyInternal("全科/内科", 0.62, "症状信息有限，建议先选择内科完成初诊分流。"));
        }
        return result;
    }

    public List<DepartmentRecommendation> normalizeAiDepartments(List<AiTriageResult.AiDepartment> aiDepartments, String fallbackText) {
        List<DepartmentRecommendation> result = new ArrayList<>();
        if (aiDepartments != null) {
            for (AiTriageResult.AiDepartment aiDepartment : aiDepartments) {
                if (aiDepartment == null || !StringUtils.hasText(aiDepartment.getName())) {
                    continue;
                }
                result.add(toAvailableDepartment(aiDepartment));
                if (result.size() >= 3) {
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            result.addAll(recommendByRules(fallbackText));
        }
        return result;
    }

    private DepartmentRecommendation toAvailableDepartment(AiTriageResult.AiDepartment aiDepartment) {
        DepartmentRecommendation matched = availableDepartments.get(aiDepartment.getName());
        if (matched != null) {
        return new DepartmentRecommendation(
                matched.getDeptId(),
                matched.getDeptCode(),
                matched.getDeptName(),
                aiDepartment.getName(),
                clamp(aiDepartment.getConfidence(), 0.55),
                StringUtils.hasText(aiDepartment.getReason()) ? aiDepartment.getReason() : matched.getReason(),
                "可点击去挂号，选择该科室号源。"
        );
        }
        return copyInternal(aiDepartment.getName(), clamp(aiDepartment.getConfidence(), 0.60),
                "AI 倾向推荐 " + aiDepartment.getName() + "，但当前系统未配置该科室，建议以内科作为首诊入口。");
    }

    private DepartmentRecommendation copyInternal(String matchedDeptName, double confidence, String reason) {
        DepartmentRecommendation internal = availableDepartments.get("内科");
        return new DepartmentRecommendation(
                internal.getDeptId(),
                internal.getDeptCode(),
                internal.getDeptName(),
                matchedDeptName,
                confidence,
                reason,
                "可点击去挂号，选择该科室号源。"
        );
    }

    private double clamp(Double confidence, double fallback) {
        double value = confidence == null ? fallback : confidence;
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
