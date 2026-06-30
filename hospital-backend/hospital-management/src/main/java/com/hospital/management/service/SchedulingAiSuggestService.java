package com.hospital.management.service;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.client.AiSchedulingFeignClient;
import com.hospital.management.dto.SchedulingUpdateRequest;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.LeaveRequestRepository;
import com.hospital.management.repository.SchedulingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulingAiSuggestService {

    private final SchedulingService schedulingService;
    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final SchedulingRepository schedulingRepository;
    private final AiSchedulingFeignClient aiSchedulingFeignClient;

    public Map<String, Object> suggest(Long deptId) {
        List<Map<String, Object>> schedules = listFrom(schedulingService.list(deptId, null, null, null));
        List<Map<String, Object>> leaveRequests = listFrom(leaveRequestService.listAdmin(1)).stream()
                .filter(row -> deptId == null || Objects.equals(asLong(row.get("deptId")), deptId))
                .toList();
        Set<Long> approvedSchedulingIds = leaveRequests.stream()
                .map(row -> asLong(row.get("schedulingId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Map<String, Object>> aiSchedules = schedules.stream()
                .filter(row -> !Boolean.TRUE.equals(row.get("pendingLeave"))
                        || approvedSchedulingIds.contains(asLong(row.get("schedulingId"))))
                .toList();
        List<Map<String, Object>> candidateDoctors = employeeRepository
                .listEmployees(null, deptId, "OUTPATIENT_DOCTOR", 0, 1, 0, 100);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deptId", deptId);
        payload.put("schedules", aiSchedules);
        payload.put("leaveRequests", leaveRequests);
        payload.put("candidateDoctors", candidateDoctors);

        Map<String, Object> aiResult;
        List<String> warnings = new ArrayList<>();
        try {
            Result<Map<String, Object>> result = aiSchedulingFeignClient.suggest(payload);
            aiResult = result != null && Boolean.TRUE.equals(result.getSuccess()) && result.getData() != null
                    ? result.getData()
                    : fallbackAiResult(schedules, leaveRequests, candidateDoctors);
            if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
                warnings.add(result != null && result.getMessage() != null
                        ? result.getMessage()
                        : "AI 排班服务暂不可用，已返回规则兜底建议");
            }
        } catch (FeignException ex) {
            aiResult = fallbackAiResult(schedules, leaveRequests, candidateDoctors);
            warnings.add("AI 排班服务暂不可用，已返回规则兜底建议");
        }

        List<Map<String, Object>> suggestions = normalizeSuggestions(aiResult, schedules, leaveRequests, candidateDoctors);
        List<Map<String, Object>> riskItems = normalizeRiskItems(aiResult, schedules, leaveRequests);
        warnings.addAll(stringList(aiResult.get("warnings")));

        return Map.of(
                "message", "AI 已生成排班建议",
                "suggestions", suggestions,
                "riskItems", riskItems,
                "warnings", warnings
        );
    }

    public Map<String, Object> replace(Long schedulingId, Map<String, Object> requestBody) {
        Map<String, Object> target = schedulingRepository.findByIdForUpdate(schedulingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在"));
        Long deptId = asLong(target.get("deptId"));
        List<Map<String, Object>> schedules = listFrom(schedulingService.list(deptId, null, null, null));
        Optional<Map<String, Object>> approvedLeave = findApprovedLeave(schedulingId, requestBody);
        if (approvedLeave.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该排班没有已批准且待替班的请假申请");
        }
        List<Map<String, Object>> leaveRequests = List.of(approvedLeave.get());
        List<Map<String, Object>> candidateDoctors = employeeRepository
                .listEmployees(null, deptId, "OUTPATIENT_DOCTOR", 0, 1, 0, 100);

        Long requestedEmployeeId = asLong(firstNonNull(
                requestBody == null ? null : requestBody.get("employeeId"),
                requestBody == null ? null : nested(requestBody, "proposedSchedule", "employeeId")
        ));
        if (requestedEmployeeId != null) {
            Map<String, Object> leave = leaveRequests.get(0);
            if (requestedEmployeeId.equals(asLong(leave.get("employeeId")))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 推荐医生不能是原请假医生");
            }
            SchedulingUpdateRequest updateRequest = new SchedulingUpdateRequest();
            updateRequest.setEmployeeId(requestedEmployeeId);
            Map<String, Object> updated = schedulingService.update(schedulingId, updateRequest);
            updated.put("message", "AI 替班已完成");
            updated.put("aiSuggestion", Map.of(
                    "schedulingId", schedulingId,
                    "replaceable", true,
                    "proposedSchedule", Map.of("employeeId", requestedEmployeeId),
                    "reason", "已应用当前 AI 排班建议"
            ));
            return updated;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deptId", deptId);
        payload.put("schedules", schedules.stream()
                .filter(row -> Objects.equals(asLong(row.get("schedulingId")), schedulingId)
                        || Objects.equals(row.get("workDate"), target.get("workDate")))
                .toList());
        payload.put("leaveRequests", leaveRequests);
        payload.put("candidateDoctors", candidateDoctors);

        Map<String, Object> aiResult;
        try {
            Result<Map<String, Object>> result = aiSchedulingFeignClient.suggest(payload);
            aiResult = result != null && Boolean.TRUE.equals(result.getSuccess()) && result.getData() != null
                    ? result.getData()
                    : fallbackAiResult(schedules, leaveRequests, candidateDoctors);
        } catch (FeignException ex) {
            aiResult = fallbackAiResult(schedules, leaveRequests, candidateDoctors);
        }

        List<Map<String, Object>> suggestions = normalizeSuggestions(aiResult, schedules, leaveRequests, candidateDoctors);
        Map<String, Object> suggestion = suggestions.stream()
                .filter(row -> Objects.equals(asLong(row.get("schedulingId")), schedulingId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "AI 暂未生成可用替班建议"));

        if (!Boolean.TRUE.equals(suggestion.get("replaceable"))) {
            String warnings = String.join("；", stringList(suggestion.get("warnings")));
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    warnings.isBlank() ? "AI 暂未找到合适替班医生" : warnings);
        }

        Long employeeId = asLong(nested(suggestion, "proposedSchedule", "employeeId"));
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 替班建议缺少替班医生");
        }

        SchedulingUpdateRequest request = new SchedulingUpdateRequest();
        request.setEmployeeId(employeeId);
        Map<String, Object> updated = schedulingService.update(schedulingId, request);
        updated.put("aiSuggestion", suggestion);
        updated.put("message", "AI 替班已完成");
        return updated;
    }

    private Optional<Map<String, Object>> findApprovedLeave(Long schedulingId, Map<String, Object> requestBody) {
        Long leaveRequestId = requestBody == null ? null : asLong(requestBody.get("leaveRequestId"));
        if (leaveRequestId != null) {
            Optional<Map<String, Object>> row = leaveRequestRepository.findById(leaveRequestId);
            if (row.isPresent()
                    && Objects.equals(asLong(row.get().get("schedulingId")), schedulingId)
                    && asInt(row.get().get("status"), -1) == 1) {
                return row;
            }
        }
        return leaveRequestRepository.findApprovedBySchedulingId(schedulingId);
    }

    private List<Map<String, Object>> normalizeSuggestions(Map<String, Object> aiResult,
                                                           List<Map<String, Object>> schedules,
                                                           List<Map<String, Object>> leaveRequests,
                                                           List<Map<String, Object>> candidateDoctors) {
        Map<Long, Map<String, Object>> scheduleById = schedules.stream()
                .collect(Collectors.toMap(row -> asLong(row.get("schedulingId")), Function.identity(), (a, b) -> a));
        Map<Long, Map<String, Object>> leaveBySchedulingId = leaveRequests.stream()
                .collect(Collectors.toMap(row -> asLong(row.get("schedulingId")), Function.identity(), (a, b) -> a));
        Map<Long, Map<String, Object>> doctorById = candidateDoctors.stream()
                .collect(Collectors.toMap(row -> asLong(row.get("employeeId")), Function.identity(), (a, b) -> a));

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> item : mapList(aiResult.get("suggestions"))) {
            Long schedulingId = asLong(item.get("schedulingId"));
            if (schedulingId == null) {
                continue;
            }
            Map<String, Object> schedule = scheduleById.get(schedulingId);
            Map<String, Object> leave = leaveBySchedulingId.get(schedulingId);
            if (schedule == null || leave == null) {
                continue;
            }

            Long recommendedEmployeeId = asLong(firstNonNull(
                    item.get("recommendedEmployeeId"),
                    nested(item, "proposedSchedule", "employeeId")
            ));
            List<String> itemWarnings = new ArrayList<>(stringList(item.get("warnings")));
            boolean replaceable = Boolean.TRUE.equals(item.get("replaceable"));
            Map<String, Object> doctor = recommendedEmployeeId == null ? null : doctorById.get(recommendedEmployeeId);
            if (doctor == null) {
                replaceable = false;
                itemWarnings.add("AI 推荐医生不在候选门诊医生列表中");
            } else {
                validateRecommendedDoctor(recommendedEmployeeId, doctor, schedule, leave, schedules, itemWarnings);
                if (!itemWarnings.isEmpty()) {
                    replaceable = false;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schedulingId", schedulingId);
            row.put("leaveRequestId", leave.get("leaveRequestId"));
            row.put("workDate", schedule.get("workDate"));
            row.put("noonLabel", schedule.get("noonLabel"));
            row.put("employeeName", schedule.get("employeeName"));
            row.put("leaveDriven", true);
            row.put("replaceable", replaceable);
            row.put("confidence", asDouble(item.get("confidence"), replaceable ? 0.75D : 0.35D));
            row.put("suggestion", text(item.get("suggestion"), replaceable && doctor != null
                    ? "建议由%s替班。".formatted(doctor.get("realName"))
                    : "暂未找到合适替班医生。"));
            row.put("reason", text(item.get("reason"), replaceable
                    ? "候选医生满足同科室门诊替班条件。"
                    : "候选医生不足或存在排班冲突，请管理员手工确认。"));
            row.put("warnings", itemWarnings);
            Map<String, Object> proposed = new LinkedHashMap<>();
            if (recommendedEmployeeId != null) {
                proposed.put("employeeId", recommendedEmployeeId);
            }
            proposed.put("totalQuota", schedule.get("totalQuota"));
            row.put("proposedSchedule", proposed);
            normalized.add(row);
        }
        return normalized;
    }

    private void validateRecommendedDoctor(Long recommendedEmployeeId,
                                           Map<String, Object> doctor,
                                           Map<String, Object> schedule,
                                           Map<String, Object> leave,
                                           List<Map<String, Object>> schedules,
                                           List<String> warnings) {
        if (recommendedEmployeeId.equals(asLong(leave.get("employeeId")))) {
            warnings.add("AI 推荐医生不能是原请假医生");
        }
        if (!Objects.equals(asLong(doctor.get("deptId")), asLong(schedule.get("deptId")))) {
            warnings.add("AI 推荐医生必须与原排班医生同科室");
        }
        if (!"OUTPATIENT_DOCTOR".equals(doctor.get("roleType"))) {
            warnings.add("AI 推荐医生必须是门诊医生");
        }
        if (asInt(doctor.get("delmark"), 1) != 0) {
            warnings.add("AI 推荐医生不是有效在职员工");
        }
        if (hasSameSlot(doctor, schedule, schedules)) {
            warnings.add("AI 推荐医生在同一天同午别已有排班");
        }
    }

    private List<Map<String, Object>> normalizeRiskItems(Map<String, Object> aiResult,
                                                         List<Map<String, Object>> schedules,
                                                         List<Map<String, Object>> leaveRequests) {
        List<Map<String, Object>> risks = new ArrayList<>();
        Set<Long> existingSchedulingIds = mapList(aiResult.get("riskItems")).stream()
                .map(row -> asLong(row.get("schedulingId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Map<String, Object> row : mapList(aiResult.get("riskItems"))) {
            Map<String, Object> risk = new LinkedHashMap<>();
            risk.put("level", text(row.get("level"), "MEDIUM"));
            risk.put("type", text(row.get("type"), "SCHEDULING_RISK"));
            risk.put("schedulingId", asLong(row.get("schedulingId")));
            risk.put("title", text(row.get("title"), "排班风险提示"));
            risk.put("description", text(row.get("description"), ""));
            risk.put("suggestion", text(row.get("suggestion"), "请管理员复核。"));
            risks.add(risk);
        }

        Map<Long, Map<String, Object>> scheduleById = schedules.stream()
                .collect(Collectors.toMap(row -> asLong(row.get("schedulingId")), Function.identity(), (a, b) -> a));
        for (Map<String, Object> leave : leaveRequests) {
            Long schedulingId = asLong(leave.get("schedulingId"));
            if (schedulingId == null || existingSchedulingIds.contains(schedulingId)) {
                continue;
            }
            Map<String, Object> schedule = scheduleById.getOrDefault(schedulingId, leave);
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

        return risks.stream()
                .sorted(Comparator.comparing(row -> riskOrder((String) row.get("level"))))
                .toList();
    }

    private Map<String, Object> fallbackAiResult(List<Map<String, Object>> schedules,
                                                 List<Map<String, Object>> leaveRequests,
                                                 List<Map<String, Object>> candidateDoctors) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Map<String, Object> leave : leaveRequests) {
            Optional<Map<String, Object>> scheduleOpt = schedules.stream()
                    .filter(row -> Objects.equals(asLong(row.get("schedulingId")), asLong(leave.get("schedulingId"))))
                    .findFirst();
            if (scheduleOpt.isEmpty()) {
                continue;
            }
            Map<String, Object> schedule = scheduleOpt.get();
            Optional<Map<String, Object>> doctor = candidateDoctors.stream()
                    .filter(row -> !Objects.equals(asLong(row.get("employeeId")), asLong(leave.get("employeeId"))))
                    .filter(row -> Objects.equals(asLong(row.get("deptId")), asLong(schedule.get("deptId"))))
                    .filter(row -> !hasSameSlot(row, schedule, schedules))
                    .findFirst();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("schedulingId", leave.get("schedulingId"));
            item.put("leaveRequestId", leave.get("leaveRequestId"));
            item.put("replaceable", doctor.isPresent());
            doctor.ifPresent(row -> {
                item.put("recommendedEmployeeId", row.get("employeeId"));
                item.put("recommendedEmployeeName", row.get("realName"));
            });
            item.put("confidence", doctor.isPresent() ? 0.65D : 0.2D);
            item.put("suggestion", doctor
                    .map(row -> "建议由%s替班。".formatted(row.get("realName")))
                    .orElse("暂未找到合适替班医生。"));
            item.put("reason", doctor.isPresent()
                    ? "该医生为同科室门诊医生，且当前班次无冲突。"
                    : "同科室候选门诊医生不足或均存在排班冲突。");
            item.put("warnings", doctor.isPresent() ? List.of() : List.of("请管理员手工安排替班"));
            suggestions.add(item);
        }
        return Map.of(
                "suggestions", suggestions,
                "riskItems", List.of(),
                "warnings", List.of("AI 服务不可用，当前为规则兜底建议")
        );
    }

    private boolean hasSameSlot(Map<String, Object> doctor, Map<String, Object> schedule,
                                List<Map<String, Object>> schedules) {
        Long employeeId = asLong(doctor.get("employeeId"));
        LocalDate workDate = (LocalDate) schedule.get("workDate");
        Integer noonType = asInt(schedule.get("noonType"), null);
        return schedules.stream().anyMatch(row ->
                employeeId.equals(asLong(row.get("employeeId")))
                        && Objects.equals(workDate, row.get("workDate"))
                        && Objects.equals(noonType, asInt(row.get("noonType"), null))
                        && asInt(row.get("publishStatus"), 1) != 2
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listFrom(Map<String, Object> page) {
        Object list = page == null ? null : page.get("list");
        if (!(list instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(Map.class::isInstance)
                .map(row -> (Map<String, Object>) row)
                .toList();
    }

    @SuppressWarnings("unchecked")
private List<Map<String, Object>> mapList(Object value) {
    if (!(value instanceof List<?> raw)) {
        return List.of();
    }

    return raw.stream()
            .filter(Map.class::isInstance)
            .map(row -> {
                Map<String, Object> map = (Map<String, Object>) row;
                Map<String, Object> copied = new LinkedHashMap<>(map);
                return copied;
            })
            .toList();
}

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream().filter(Objects::nonNull).map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private Object nested(Map<String, Object> row, String mapKey, String valueKey) {
        Object value = row.get(mapKey);
        if (value instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get(valueKey);
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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

    private Double asDouble(Object value, Double defaultValue) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s && !s.isBlank()) return Double.parseDouble(s);
        return defaultValue;
    }

    private String text(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private int riskOrder(String level) {
        return switch (level) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }
}
