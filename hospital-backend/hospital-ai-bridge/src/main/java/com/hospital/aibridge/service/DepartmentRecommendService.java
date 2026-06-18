package com.hospital.aibridge.service;

import com.hospital.aibridge.dto.AiTriageResult;
import com.hospital.aibridge.dto.DepartmentRecommendation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DepartmentRecommendService {

    private final JdbcClient jdbcClient;
    private final Map<String, DepartmentRecommendation> fallbackDepartments = new LinkedHashMap<>();

    public DepartmentRecommendService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        registerDepartment(1L, "INTERNAL", "内科", "可作为常见症状首诊科室。");
        registerDepartment(7L, "SURGERY", "外科", "可处理外伤、肿块、外科相关不适。");
        registerDepartment(8L, "PEDIATRICS", "儿科", "适合儿童常见不适首诊。");
        registerDepartment(9L, "OBGYN", "妇产科", "适合孕产、妇科相关问题首诊。");
    }

    public List<DepartmentRecommendation> recommendByRules(String text) {
        String value = StringUtils.hasText(text) ? text.toLowerCase(Locale.ROOT) : "";
        List<DepartmentRecommendation> result = new ArrayList<>();
        if (containsAny(value, "儿童", "小孩", "宝宝", "幼儿", "孩子")) {
            result.add(copyDepartment("儿科", "儿科", 0.82, "患者为儿童或儿童相关症状，建议先到儿科进行初诊评估。"));
        } else if (containsAny(value, "孕", "产检", "妇科", "月经", "阴道", "产后")) {
            result.add(copyDepartment("妇产科", "妇产科", 0.82, "描述包含孕产或妇科相关信息，建议先到妇产科评估。"));
        } else if (containsAny(value, "外伤", "肿块", "摔", "扭伤", "伤口", "骨折")) {
            result.add(copyDepartment("外科", "外科", 0.78, "描述包含外伤或外科相关不适，建议先到外科评估。"));
        } else if (containsAny(value, "头痛", "头晕", "恶心", "发热", "咳嗽", "咽痛", "腹痛", "腹泻", "乏力", "心慌")) {
            result.add(copyDepartment("内科", "内科", 0.78, "根据症状描述，建议先到内科进行初诊评估。"));
        } else {
            result.add(copyDepartment("内科", "全科/内科", 0.62, "症状信息有限，建议先选择内科完成初诊分流。"));
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

    public List<String> availableDepartmentNames() {
        return new ArrayList<>(availableDepartments().keySet());
    }

    private DepartmentRecommendation toAvailableDepartment(AiTriageResult.AiDepartment aiDepartment) {
        DepartmentRecommendation matched = findAvailableDepartment(aiDepartment.getName());
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
        return copyDepartment("内科", aiDepartment.getName(), clamp(aiDepartment.getConfidence(), 0.60),
                "AI 倾向推荐 " + aiDepartment.getName() + "，但当前系统未配置该科室，建议以内科作为首诊入口。");
    }

    private void registerDepartment(Long deptId, String deptCode, String deptName, String reason) {
        fallbackDepartments.put(deptName, new DepartmentRecommendation(deptId, deptCode, deptName, null, 0.70,
                "当前系统已配置" + deptName + "，" + reason,
                "可点击去挂号，选择该科室号源。"));
    }

    private DepartmentRecommendation findAvailableDepartment(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        Map<String, DepartmentRecommendation> departments = availableDepartments();
        DepartmentRecommendation exact = departments.get(name);
        if (exact != null) {
            return exact;
        }
        String aliasDept = inferDepartmentByAlias(name);
        if (aliasDept != null) {
            return departments.get(aliasDept);
        }
        for (Map.Entry<String, DepartmentRecommendation> entry : departments.entrySet()) {
            if (name.contains(entry.getKey()) || entry.getKey().contains(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String inferDepartmentByAlias(String name) {
        if (containsAny(name, "呼吸", "消化", "神经", "心内", "心血管", "内分泌", "肾内", "血液", "全科")) {
            return "内科";
        }
        if (containsAny(name, "普外", "骨科", "泌尿", "肛肠", "创伤", "伤口", "烧伤", "整形")) {
            return "外科";
        }
        if (containsAny(name, "小儿", "儿内", "儿童", "儿保")) {
            return "儿科";
        }
        if (containsAny(name, "妇科", "产科", "产检", "孕产", "计划生育")) {
            return "妇产科";
        }
        return null;
    }

    private DepartmentRecommendation copyDepartment(String deptName, String matchedDeptName, double confidence, String reason) {
        DepartmentRecommendation department = availableDepartments().getOrDefault(deptName, fallbackDepartments.get("内科"));
        return new DepartmentRecommendation(
                department.getDeptId(),
                department.getDeptCode(),
                department.getDeptName(),
                matchedDeptName,
                confidence,
                reason,
                "可点击去挂号，选择该科室号源。"
        );
    }

    private Map<String, DepartmentRecommendation> availableDepartments() {
        try {
            Map<String, DepartmentRecommendation> result = new LinkedHashMap<>();
            jdbcClient.sql("""
                            SELECT id, dept_code, dept_name
                            FROM department
                            WHERE delmark = 0 AND dept_type = 1
                            ORDER BY sort_no, id
                            """)
                    .query((rs, rowNum) -> new DepartmentRecommendation(
                            rs.getLong("id"),
                            rs.getString("dept_code"),
                            rs.getString("dept_name"),
                            null,
                            0.70,
                            "当前系统已配置" + rs.getString("dept_name") + "，可作为门诊首诊科室。",
                            "可点击去挂号，选择该科室号源。"
                    ))
                    .list()
                    .forEach(department -> result.put(department.getDeptName(), department));
            return result.isEmpty() ? fallbackDepartments : result;
        } catch (Exception ex) {
            return fallbackDepartments;
        }
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
