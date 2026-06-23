package com.hospital.aibridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.aibridge.config.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class SchedulingAiService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public SchedulingAiService(AiProperties aiProperties,
                               ObjectMapper objectMapper,
                               ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
    }

    public Map<String, Object> suggest(Map<String, Object> request) {
        Map<String, Object> fallback = fallbackSuggest(request);
        if (!enabled()) {
            return fallback;
        }

        String prompt = """
                请根据当前排班、已批准请假和候选医生列表，完成两项任务：

                1. 为已批准且未替班的请假班次推荐替班医生。
                2. 分析当前排班风险。

                业务规则：
                - 请假医生不能作为自己的替班人。
                - 替班医生必须来自 candidateDoctors。
                - 替班医生必须是同科室门诊医生。
                - 同一医生同一天同午别不要重复排班。
                - 已有挂号 usedQuota > 0 的班次不能建议删除。
                - 如果无法判断，请给 warning，不要强行推荐。

                输出严格 JSON，不要 Markdown。JSON 字段固定为：
                {
                  "suggestions": [
                    {
                      "schedulingId": 10,
                      "leaveRequestId": 3,
                      "recommendedEmployeeId": 1002,
                      "recommendedEmployeeName": "李医生",
                      "replaceable": true,
                      "confidence": 0.86,
                      "suggestion": "建议由李医生替班。",
                      "reason": "李医生属于同科室门诊医生，当前日期上午未排班。",
                      "warnings": []
                    }
                  ],
                  "riskItems": [
                    {
                      "level": "HIGH",
                      "type": "LEAVE_NOT_REPLACED",
                      "schedulingId": 10,
                      "title": "已批准请假但尚未替班",
                      "description": "张医生该班次已批准请假，但仍未完成替班。",
                      "suggestion": "建议尽快安排同科室医生替班。"
                    }
                  ],
                  "warnings": []
                }

                输入数据：
                %s
                """.formatted(writeJson(request));

        return callJson(prompt).map(this::normalize).orElse(fallback);
    }

    private boolean enabled() {
        return aiProperties.isEnabled() && chatClient != null;
    }

    private Optional<Map<String, Object>> callJson(String prompt) {
        try {
            String content = chatClient.prompt()
                    .system("""
                            你是医院后台 AI 排班助手。
                            你只能基于输入 JSON 数据生成排班建议，不能虚构医生、科室、日期、班次或号别。
                            你不能直接修改排班，只能给管理员提供建议。
                            recommendedEmployeeId 必须来自 candidateDoctors。
                            如果没有合适替班医生，replaceable 必须为 false。
                            请只输出严格 JSON，不要输出 Markdown。
                            """)
                    .user(prompt)
                    .call()
                    .content();
            return Optional.of(objectMapper.readValue(cleanJson(content), new TypeReference<>() {
            }));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Map<String, Object> normalize(Map<String, Object> result) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("suggestions", mapList(result.get("suggestions")));
        normalized.put("riskItems", mapList(result.get("riskItems")));
        normalized.put("warnings", stringList(result.get("warnings")));
        return normalized;
    }

    private Map<String, Object> fallbackSuggest(Map<String, Object> request) {
        List<Map<String, Object>> schedules = mapList(request.get("schedules"));
        List<Map<String, Object>> leaveRequests = mapList(request.get("leaveRequests"));
        List<Map<String, Object>> candidateDoctors = mapList(request.get("candidateDoctors"));

        List<Map<String, Object>> suggestions = new ArrayList<>();
        List<Map<String, Object>> risks = new ArrayList<>();
        for (Map<String, Object> leave : leaveRequests) {
            Long schedulingId = asLong(leave.get("schedulingId"));
            Optional<Map<String, Object>> scheduleOpt = schedules.stream()
                    .filter(row -> Objects.equals(asLong(row.get("schedulingId")), schedulingId))
                    .findFirst();
            Map<String, Object> schedule = scheduleOpt.orElse(leave);
            Optional<Map<String, Object>> doctor = candidateDoctors.stream()
                    .filter(row -> !Objects.equals(asLong(row.get("employeeId")), asLong(leave.get("employeeId"))))
                    .filter(row -> Objects.equals(asLong(row.get("deptId")), asLong(schedule.get("deptId"))))
                    .filter(row -> !hasSameSlot(row, schedule, schedules))
                    .findFirst();

            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("schedulingId", schedulingId);
            suggestion.put("leaveRequestId", leave.get("leaveRequestId"));
            suggestion.put("replaceable", doctor.isPresent());
            doctor.ifPresent(row -> {
                suggestion.put("recommendedEmployeeId", row.get("employeeId"));
                suggestion.put("recommendedEmployeeName", row.get("realName"));
            });
            suggestion.put("confidence", doctor.isPresent() ? 0.65D : 0.2D);
            suggestion.put("suggestion", doctor
                    .map(row -> "建议由%s替班。".formatted(row.get("realName")))
                    .orElse("暂未找到合适替班医生。"));
            suggestion.put("reason", doctor.isPresent()
                    ? "该医生为同科室门诊医生，且当前班次无冲突。"
                    : "同科室候选门诊医生不足或均存在排班冲突。");
            suggestion.put("warnings", doctor.isPresent() ? List.of() : List.of("请管理员手工安排替班"));
            suggestions.add(suggestion);

            risks.add(Map.of(
                    "level", "HIGH",
                    "type", "LEAVE_NOT_REPLACED",
                    "schedulingId", schedulingId,
                    "title", "已批准请假但尚未替班",
                    "description", "%s %s %s 已批准请假，但该班次仍需安排替班。".formatted(
                            text(schedule.get("employeeName"), text(leave.get("employeeName"), "医生")),
                            text(schedule.get("workDate"), text(leave.get("workDate"), "")),
                            text(schedule.get("noonLabel"), text(leave.get("noonLabel"), ""))
                    ),
                    "suggestion", "建议尽快安排同科室门诊医生替班。"
            ));
        }

        return Map.of(
                "suggestions", suggestions,
                "riskItems", risks,
                "warnings", List.of("AI 模型不可用，已返回规则兜底建议")
        );
    }

    private boolean hasSameSlot(Map<String, Object> doctor, Map<String, Object> schedule,
                                List<Map<String, Object>> schedules) {
        Long employeeId = asLong(doctor.get("employeeId"));
        String workDate = dateText(schedule.get("workDate"));
        Integer noonType = asInt(schedule.get("noonType"), null);
        return schedules.stream().anyMatch(row ->
                employeeId.equals(asLong(row.get("employeeId")))
                        && Objects.equals(workDate, dateText(row.get("workDate")))
                        && Objects.equals(noonType, asInt(row.get("noonType"), null))
                        && asInt(row.get("publishStatus"), 1) != 2
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(Map.class::isInstance)
                .map(row -> (Map<String, Object>) new LinkedHashMap<>((Map<String, Object>) row))
                .toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);
        return null;
    }

    private Integer asInt(Object value, Integer defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s && !s.isBlank()) return Integer.parseInt(s);
        return defaultValue;
    }

    private String text(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private String dateText(Object value) {
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return value == null ? null : String.valueOf(value);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String cleanJson(String content) {
        if (content == null) {
            return "{}";
        }
        String value = content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }
}
