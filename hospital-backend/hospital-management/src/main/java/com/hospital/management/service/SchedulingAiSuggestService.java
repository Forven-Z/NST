package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.SchedulingUpdateRequest;
import com.hospital.management.repository.DictRepository;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.LeaveRequestRepository;
import com.hospital.management.repository.SchedulingRepository;
import com.hospital.management.support.NoonTypeSupport;
import com.hospital.management.support.RegistLevelQuotaDefaults;
import com.hospital.management.support.WeekStartSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulingAiSuggestService {

    private static final String MODE_WEEK = "WEEK";
    private static final String MODE_SUBSTITUTE = "SUBSTITUTE";
    private static final long NORMAL_LEVEL_FALLBACK = 1L;
    private static final long EXPERT_LEVEL_FALLBACK = 2L;

    private final SchedulingService schedulingService;
    private final LeaveRequestService leaveRequestService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final SchedulingRepository schedulingRepository;
    private final DictRepository dictRepository;

    public Map<String, Object> suggest(Long deptId, LocalDate weekStart, String mode) {
        return suggest(deptId, weekStart, mode, null);
    }

    public Map<String, Object> suggest(Long deptId, LocalDate weekStart, String mode, String rulesText) {
        String normalizedMode = mode == null || mode.isBlank() ? MODE_WEEK : mode.trim().toUpperCase();
        SchedulingRules rules = SchedulingRules.parse(rulesText);
        if (MODE_SUBSTITUTE.equals(normalizedMode)) {
            return suggestSubstitutes(deptId, rules);
        }
        return suggestWeek(deptId, weekStart, rules);
    }

    public Map<String, Object> replace(Long schedulingId, Map<String, Object> requestBody) {
        Map<String, Object> target = schedulingRepository.findByIdForUpdate(schedulingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "未找到目标排班"));
        Long deptId = asLong(target.get("deptId"));
        List<Map<String, Object>> schedules = listFrom(schedulingService.list(deptId, null, null, null));
        Optional<Map<String, Object>> approvedLeave = findApprovedLeave(schedulingId, requestBody);
        if (approvedLeave.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已审批通过的请假排班可以生成替班建议");
        }

        Long requestedEmployeeId = asLong(firstNonNull(
                requestBody == null ? null : requestBody.get("employeeId"),
                requestBody == null ? null : nested(requestBody, "proposedSchedule", "employeeId")
        ));
        if (requestedEmployeeId != null) {
            validateSubstituteDoctor(requestedEmployeeId, approvedLeave.get(), target, schedules);
            SchedulingUpdateRequest updateRequest = new SchedulingUpdateRequest();
            updateRequest.setEmployeeId(requestedEmployeeId);
            Map<String, Object> updated = schedulingService.update(schedulingId, updateRequest);
            updated.put("message", "AI 替班已应用");
            updated.put("aiSuggestion", Map.of(
                    "schedulingId", schedulingId,
                    "replaceable", true,
                    "proposedSchedule", Map.of("employeeId", requestedEmployeeId),
                    "reason", "管理员已确认并应用 AI 替班建议"
            ));
            return updated;
        }

        Map<String, Object> suggestion = buildSubstituteSuggestion(
                target,
                approvedLeave.get(),
                schedules,
                outpatientDoctors(deptId)
        );
        if (!Boolean.TRUE.equals(suggestion.get("replaceable"))) {
            String warnings = String.join("；", stringList(suggestion.get("warnings")));
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    warnings.isBlank() ? "AI 未找到可用替班医生" : warnings
            );
        }

        Long employeeId = asLong(nested(suggestion, "proposedSchedule", "employeeId"));
        SchedulingUpdateRequest request = new SchedulingUpdateRequest();
        request.setEmployeeId(employeeId);
        Map<String, Object> updated = schedulingService.update(schedulingId, request);
        updated.put("aiSuggestion", suggestion);
        updated.put("message", "AI 替班已应用");
        return updated;
    }

    private Map<String, Object> suggestWeek(Long deptId, LocalDate requestedWeekStart, SchedulingRules rules) {
        if (deptId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先选择科室后再获取 AI 排班建议");
        }

        LocalDate weekStart = WeekStartSupport.alignToMonday(
                requestedWeekStart == null ? LocalDate.now() : requestedWeekStart
        );
        LocalDate weekEnd = WeekStartSupport.weekEnd(weekStart);
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> doctors = outpatientDoctors(deptId);
        if (doctors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前科室没有可用于门诊排班的医生");
        }

        List<Map<String, Object>> existing = schedulingRepository.listWeekByDept(deptId, weekStart, weekEnd);
        Set<String> occupied = existing.stream()
                .filter(row -> asInt(row.get("publishStatus"), 1) != 2)
                .map(row -> slotKey(
                        asLong(row.get("employeeId")),
                        (LocalDate) row.get("workDate"),
                        asInt(row.get("noonType"), null)
                ))
                .collect(Collectors.toCollection(HashSet::new));

        Long normalLevelId = registLevelId("NORMAL", NORMAL_LEVEL_FALLBACK);
        Long expertLevelId = registLevelId("EXPERT", EXPERT_LEVEL_FALLBACK);
        List<Map<String, Object>> regularDoctors = doctors.stream()
                .filter(row -> !isExpert(row))
                .toList();
        List<Map<String, Object>> expertDoctors = doctors.stream()
                .filter(this::isExpert)
                .toList();

        List<Map<String, Object>> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (int doctorIndex = 0; doctorIndex < regularDoctors.size(); doctorIndex++) {
            Map<String, Object> doctor = regularDoctors.get(doctorIndex);
            int restDay = rules.regularSessionsPerDoctor >= 12 && !rules.weekendOff ? doctorIndex % 7 : -1;
            int added = 0;
            for (int scan = 0; scan < 14 && added < rules.regularSessionsPerDoctor; scan++) {
                int slotIndex = (doctorIndex * 2 + scan) % 14;
                int day = slotIndex / 2;
                if (day == restDay || rules.skipDay(day)) {
                    continue;
                }
                LocalDate workDate = weekStart.plusDays(day);
                int noonType = slotIndex % 2 == 0 ? 1 : 2;
                if (addChangeIfPossible(
                        changes,
                        occupied,
                        doctor,
                        workDate,
                        noonType,
                        normalLevelId,
                        rules.normalQuota(RegistLevelQuotaDefaults.defaultQuota(normalLevelId)),
                        today
                )) {
                    added++;
                }
            }
        }

        for (int doctorIndex = 0; doctorIndex < expertDoctors.size(); doctorIndex++) {
            Map<String, Object> doctor = expertDoctors.get(doctorIndex);
            int start = doctorIndex * 3;
            int added = 0;
            for (int scan = 0; scan < 14 && added < rules.expertSessionsPerDoctor; scan++) {
                int slotIndex = (start + scan * 5) % 14;
                int day = slotIndex / 2;
                if (rules.skipDay(day)) {
                    continue;
                }
                LocalDate workDate = weekStart.plusDays(day);
                int noonType = slotIndex % 2 == 0 ? 1 : 2;
                if (addChangeIfPossible(
                        changes,
                        occupied,
                        doctor,
                        workDate,
                        noonType,
                        expertLevelId,
                        rules.expertQuota(RegistLevelQuotaDefaults.defaultQuota(expertLevelId)),
                        today
                )) {
                    added++;
                }
            }
        }

        for (int day = 0; day < 7; day++) {
            if (rules.skipDay(day)) {
                continue;
            }
            LocalDate workDate = weekStart.plusDays(day);
            for (int noonType : List.of(1, 2)) {
                if (isSlotCovered(workDate, noonType, existing, changes)) {
                    continue;
                }
                Optional<Map<String, Object>> regularFiller = regularDoctors.stream()
                        .filter(row -> !occupied.contains(slotKey(asLong(row.get("employeeId")), workDate, noonType)))
                        .findFirst();
                Optional<Map<String, Object>> filler = regularFiller.isPresent()
                        ? regularFiller
                        : !rules.allowExpertFallback
                        ? Optional.empty()
                        : expertDoctors.stream()
                        .filter(row -> !occupied.contains(slotKey(asLong(row.get("employeeId")), workDate, noonType)))
                        .findFirst();

                if (filler.isPresent()) {
                    boolean expert = isExpert(filler.get());
                    Long registLevelId = expert ? expertLevelId : normalLevelId;
                    addChangeIfPossible(
                            changes,
                            occupied,
                            filler.get(),
                            workDate,
                            noonType,
                            registLevelId,
                            expert
                                    ? rules.expertQuota(RegistLevelQuotaDefaults.defaultQuota(registLevelId))
                                    : rules.normalQuota(RegistLevelQuotaDefaults.defaultQuota(registLevelId)),
                            today
                    );
                    if (expert) {
                        warnings.add("%s %s 无普通医生可补位，已使用专家医生兜底：%s".formatted(
                                workDate,
                                NoonTypeSupport.label(noonType),
                                text(filler.get().get("realName"), "未命名医生")
                        ));
                    }
                } else {
                    warnings.add("%s %s 无法满足至少 1 名医生出诊，请人工补充排班".formatted(
                            workDate,
                            NoonTypeSupport.label(noonType)
                    ));
                }
            }
        }
        ensureMinSlotCoverage(
                weekStart,
                existing,
                changes,
                occupied,
                regularDoctors,
                expertDoctors,
                normalLevelId,
                expertLevelId,
                today,
                rules,
                warnings
        );
        warnings.addAll(rules.warnings());

        return Map.of(
                "mode", MODE_WEEK,
                "weekStart", weekStart,
                "weekEnd", weekEnd,
                "changes", changes,
                "suggestions", List.of(),
                "riskItems", List.of(),
                "warnings", warnings,
                "message", "AI 已生成本周排班草稿，请确认后保存"
        );
    }

    private Map<String, Object> suggestSubstitutes(Long deptId, SchedulingRules rules) {
        List<Map<String, Object>> schedules = listFrom(schedulingService.list(deptId, null, null, null));
        List<Map<String, Object>> leaveRequests = listFrom(leaveRequestService.listAdmin(1)).stream()
                .filter(row -> deptId == null || Objects.equals(asLong(row.get("deptId")), deptId))
                .toList();
        List<Map<String, Object>> candidateDoctors = outpatientDoctors(deptId);

        Map<Long, Map<String, Object>> scheduleById = schedules.stream()
                .collect(Collectors.toMap(row -> asLong(row.get("schedulingId")), Function.identity(), (a, b) -> a));
        List<Map<String, Object>> suggestions = new ArrayList<>();
        List<Map<String, Object>> risks = new ArrayList<>();

        for (Map<String, Object> leave : leaveRequests) {
            Map<String, Object> schedule = scheduleById.get(asLong(leave.get("schedulingId")));
            if (schedule == null) {
                continue;
            }
            Map<String, Object> suggestion = buildSubstituteSuggestion(schedule, leave, schedules, candidateDoctors);
            suggestions.add(suggestion);
            risks.add(Map.of(
                    "level", "HIGH",
                    "type", "LEAVE_NOT_REPLACED",
                    "schedulingId", leave.get("schedulingId"),
                    "title", "请假排班待处理",
                    "description", "%s 于 %s %s 请假，需尽快安排替班".formatted(
                            text(schedule.get("employeeName"), text(leave.get("employeeName"), "未知医生")),
                            text(schedule.get("workDate"), text(leave.get("workDate"), "")),
                            text(schedule.get("noonLabel"), text(leave.get("noonLabel"), ""))
                    ),
                    "suggestion", Boolean.TRUE.equals(suggestion.get("replaceable"))
                            ? text(suggestion.get("suggestion"), "可直接应用 AI 替班建议")
                            : "暂未找到符合条件的同类型替班医生，请人工处理"
            ));
        }

        return Map.of(
                "mode", MODE_SUBSTITUTE,
                "changes", List.of(),
                "suggestions", suggestions,
                "riskItems", risks.stream()
                        .sorted(Comparator.comparing(row -> riskOrder((String) row.get("level"))))
                        .toList(),
                "warnings", rules.warnings(),
                "message", suggestions.isEmpty() ? "当前没有待处理的请假替班记录" : "AI 已生成替班建议"
        );
    }

    private boolean addChangeIfPossible(List<Map<String, Object>> changes,
                                        Set<String> occupied,
                                        Map<String, Object> doctor,
                                        LocalDate workDate,
                                        int noonType,
                                        Long registLevelId,
                                        int totalQuota,
                                        LocalDate today) {
        if (workDate.isBefore(today)) {
            return false;
        }
        Long employeeId = asLong(doctor.get("employeeId"));
        String key = slotKey(employeeId, workDate, noonType);
        if (occupied.contains(key)) {
            return false;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("employeeId", employeeId);
        change.put("workDate", workDate);
        change.put("noonType", noonType);
        change.put("registLevelId", registLevelId);
        change.put("totalQuota", totalQuota);
        changes.add(change);
        occupied.add(key);
        return true;
    }

    private boolean isSlotCovered(LocalDate workDate,
                                  int noonType,
                                  List<Map<String, Object>> existing,
                                  List<Map<String, Object>> changes) {
        boolean existingCovered = existing.stream().anyMatch(row ->
                Objects.equals(row.get("workDate"), workDate)
                        && asInt(row.get("noonType"), null) == noonType
                        && asInt(row.get("publishStatus"), 1) != 2
        );
        if (existingCovered) {
            return true;
        }
        return changes.stream().anyMatch(row ->
                Objects.equals(row.get("workDate"), workDate)
                        && asInt(row.get("noonType"), null) == noonType
        );
    }

    private long slotCoverage(LocalDate workDate,
                              int noonType,
                              List<Map<String, Object>> existing,
                              List<Map<String, Object>> changes) {
        long existingCovered = existing.stream().filter(row ->
                Objects.equals(row.get("workDate"), workDate)
                        && asInt(row.get("noonType"), null) == noonType
                        && asInt(row.get("publishStatus"), 1) != 2
        ).count();
        long draftCovered = changes.stream().filter(row ->
                Objects.equals(row.get("workDate"), workDate)
                        && asInt(row.get("noonType"), null) == noonType
        ).count();
        return existingCovered + draftCovered;
    }

    private void ensureMinSlotCoverage(LocalDate weekStart,
                                       List<Map<String, Object>> existing,
                                       List<Map<String, Object>> changes,
                                       Set<String> occupied,
                                       List<Map<String, Object>> regularDoctors,
                                       List<Map<String, Object>> expertDoctors,
                                       Long normalLevelId,
                                       Long expertLevelId,
                                       LocalDate today,
                                       SchedulingRules rules,
                                       List<String> warnings) {
        if (rules.minDoctorsPerSlot <= 1) {
            return;
        }
        for (int day = 0; day < 7; day++) {
            if (rules.skipDay(day)) {
                continue;
            }
            LocalDate workDate = weekStart.plusDays(day);
            for (int noonType : List.of(1, 2)) {
                while (slotCoverage(workDate, noonType, existing, changes) < rules.minDoctorsPerSlot) {
                    Optional<Map<String, Object>> regularFiller = regularDoctors.stream()
                            .filter(row -> !occupied.contains(slotKey(asLong(row.get("employeeId")), workDate, noonType)))
                            .findFirst();
                    Optional<Map<String, Object>> filler = regularFiller.isPresent()
                            ? regularFiller
                            : rules.allowExpertFallback
                            ? expertDoctors.stream()
                            .filter(row -> !occupied.contains(slotKey(asLong(row.get("employeeId")), workDate, noonType)))
                            .findFirst()
                            : Optional.empty();
                    if (filler.isEmpty()) {
                        warnings.add("%s %s 无法满足至少 %d 名医生出诊，请人工补充排班".formatted(
                                workDate,
                                NoonTypeSupport.label(noonType),
                                rules.minDoctorsPerSlot
                        ));
                        break;
                    }

                    boolean expert = isExpert(filler.get());
                    Long registLevelId = expert ? expertLevelId : normalLevelId;
                    addChangeIfPossible(
                            changes,
                            occupied,
                            filler.get(),
                            workDate,
                            noonType,
                            registLevelId,
                            expert
                                    ? rules.expertQuota(RegistLevelQuotaDefaults.defaultQuota(registLevelId))
                                    : rules.normalQuota(RegistLevelQuotaDefaults.defaultQuota(registLevelId)),
                            today
                    );
                }
            }
        }
    }

    private Map<String, Object> buildSubstituteSuggestion(Map<String, Object> schedule,
                                                          Map<String, Object> leave,
                                                          List<Map<String, Object>> schedules,
                                                          List<Map<String, Object>> candidateDoctors) {
        Long originalEmployeeId = asLong(leave.get("employeeId"));
        boolean sourceIsExpert = isExpertSchedule(schedule);
        Optional<Map<String, Object>> doctor = candidateDoctors.stream()
                .filter(row -> !Objects.equals(asLong(row.get("employeeId")), originalEmployeeId))
                .filter(row -> Objects.equals(asLong(row.get("deptId")), asLong(schedule.get("deptId"))))
                .filter(row -> isExpert(row) == sourceIsExpert)
                .filter(row -> !hasSameSlot(row, schedule, schedules))
                .findFirst();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("schedulingId", schedule.get("schedulingId"));
        row.put("leaveRequestId", leave.get("leaveRequestId"));
        row.put("workDate", schedule.get("workDate"));
        row.put("noonLabel", schedule.get("noonLabel"));
        row.put("employeeName", schedule.get("employeeName"));
        row.put("leaveDriven", true);
        row.put("replaceable", doctor.isPresent());
        row.put("confidence", doctor.isPresent() ? 0.82D : 0.25D);
        row.put(
                "warnings",
                doctor.isPresent()
                        ? List.of()
                        : List.of("未找到同科室、同类型且该时段空闲的替班医生")
        );
        row.put(
                "suggestion",
                doctor.map(item -> "建议由 %s 替班".formatted(text(item.get("realName"), "未命名医生")))
                        .orElse("暂未找到可直接替班的医生")
        );
        row.put(
                "reason",
                doctor.isPresent()
                        ? "候选医生与请假排班同科室、同医生类型，且该半天没有排班冲突"
                        : "当前没有满足同科室、同医生类型且无排班冲突的候选医生"
        );
        Map<String, Object> proposed = new LinkedHashMap<>();
        doctor.ifPresent(item -> proposed.put("employeeId", item.get("employeeId")));
        proposed.put("totalQuota", schedule.get("totalQuota"));
        row.put("proposedSchedule", proposed);
        return row;
    }

    private void validateSubstituteDoctor(Long employeeId,
                                          Map<String, Object> leave,
                                          Map<String, Object> schedule,
                                          List<Map<String, Object>> schedules) {
        if (employeeId.equals(asLong(leave.get("employeeId")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能为请假医生本人安排替班");
        }
        Map<String, Object> doctor = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "替班医生不存在"));
        if (!Objects.equals(asLong(doctor.get("deptId")), asLong(schedule.get("deptId")))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "替班医生必须与原排班属于同一科室");
        }
        if (!"OUTPATIENT_DOCTOR".equals(doctor.get("roleType"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "替班医生必须是门诊医生");
        }
        if (isExpert(doctor) != isExpertSchedule(schedule)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "普通医生和专家医生之间不能互相替班");
        }
        if (hasSameSlot(doctor, schedule, schedules)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "替班医生在同日期同午别已有排班");
        }
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

    private List<Map<String, Object>> outpatientDoctors(Long deptId) {
        return employeeRepository
                .listEmployees(null, deptId, "OUTPATIENT_DOCTOR", 0, 1, 0, 200)
                .stream()
                .sorted(Comparator.comparing(row -> asLong(row.get("employeeId"))))
                .toList();
    }

    private Long registLevelId(String levelCode, Long fallback) {
        return dictRepository.listRegistLevels(null, 0, 100)
                .stream()
                .filter(row -> levelCode.equalsIgnoreCase(text(row.get("levelCode"), "")))
                .map(row -> asLong(row.get("id")))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }

    private boolean isExpert(Map<String, Object> doctor) {
        String title = text(doctor.get("title"), "");
        return title.contains("主任医师")
                || title.contains("副主任医师")
                || title.contains("教授")
                || title.contains("专家")
                || title.toUpperCase().contains("EXPERT");
    }

    private boolean isExpertSchedule(Map<String, Object> schedule) {
        Long registLevelId = asLong(schedule.get("registLevelId"));
        String registLevelName = text(schedule.get("registLevelName"), "");
        if (Objects.equals(registLevelId, EXPERT_LEVEL_FALLBACK)) {
            return true;
        }
        return registLevelName.contains("专家");
    }

    private boolean hasSameSlot(Map<String, Object> doctor,
                                Map<String, Object> schedule,
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
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Long.parseLong(s);
        }
        return null;
    }

    private Integer asInt(Object value, Integer defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Integer.parseInt(s);
        }
        return defaultValue;
    }

    private String text(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private String slotKey(Long employeeId, LocalDate workDate, Integer noonType) {
        return employeeId + "|" + workDate + "|" + noonType;
    }

    private int riskOrder(String level) {
        return switch (level) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private static final class SchedulingRules {
        private static final Pattern HALF_DAY_PATTERN = Pattern.compile("(\\d+)\\s*(?:个)?(?:半天|halfdays?|half-days?)", Pattern.CASE_INSENSITIVE);
        private static final Pattern DAY_PATTERN = Pattern.compile("(\\d+)\\s*(?:天|days?)", Pattern.CASE_INSENSITIVE);
        private static final Pattern QUOTA_PATTERN = Pattern.compile("(?:号额|号源|名额|限号|限额|quota)\\D{0,8}(\\d+)|(\\d+)\\s*(?:个)?号");
        private static final Pattern MIN_DOCTOR_PATTERN = Pattern.compile("(?:至少|不少于|最低|最少)\\D{0,8}(\\d+)\\s*(?:名|个)?(?:医生|人)");

        private int regularSessionsPerDoctor = 12;
        private int expertSessionsPerDoctor = 3;
        private Integer normalQuota;
        private Integer expertQuota;
        private int minDoctorsPerSlot = 1;
        private boolean weekendOff;
        private boolean allowExpertFallback = true;
        private final List<String> warnings = new ArrayList<>();

        static SchedulingRules parse(String rulesText) {
            SchedulingRules rules = new SchedulingRules();
            if (rulesText == null || rulesText.isBlank()) {
                return rules;
            }

            String normalized = rulesText.replace('：', ':').replace('，', ',').trim();
            for (String rawLine : normalized.split("[\\r\\n;；]+")) {
                String line = rawLine.trim();
                if (line.isBlank()) {
                    continue;
                }
                rules.applyLine(line);
            }
            rules.warnings.add("已按管理员输入规则生成排班；可识别规则包括每周半天数、号额、每半天最少医生数、周末休息、专家兜底。");
            return rules;
        }

        private void applyLine(String line) {
            String lowerLine = line.toLowerCase();
            boolean regularLine = line.contains("普通") || lowerLine.contains("regular") || lowerLine.contains("normal");
            boolean expertLine = line.contains("专家") || line.contains("主任") || line.contains("教授") || lowerLine.contains("expert");

            Integer sessions = extractSessions(line);
            if (sessions != null) {
                if (expertLine && !regularLine) {
                    expertSessionsPerDoctor = clamp(sessions, 0, 14);
                } else if (regularLine) {
                    regularSessionsPerDoctor = clamp(sessions, 0, 14);
                }
            }

            Integer quota = extractQuota(line);
            if (quota != null) {
                if (expertLine && !regularLine) {
                    expertQuota = clamp(quota, 1, 500);
                } else if (regularLine) {
                    normalQuota = clamp(quota, 1, 500);
                }
            }

            Integer minDoctors = extractFirst(line, MIN_DOCTOR_PATTERN);
            if (minDoctors != null) {
                minDoctorsPerSlot = clamp(minDoctors, 1, 20);
            }

            if ((line.contains("周末") || lowerLine.contains("weekend"))
                    && (line.contains("休") || line.contains("不排") || line.contains("停诊") || lowerLine.contains("off"))) {
                weekendOff = true;
            }
            if ((line.contains("不允许") || line.contains("禁止") || line.contains("不要"))
                    && line.contains("专家")
                    && (line.contains("兜底") || line.contains("补位") || line.contains("补班"))) {
                allowExpertFallback = false;
            }
        }

        private Integer extractSessions(String line) {
            Matcher halfDayMatcher = HALF_DAY_PATTERN.matcher(line);
            if (halfDayMatcher.find()) {
                return Integer.parseInt(halfDayMatcher.group(1));
            }
            Matcher dayMatcher = DAY_PATTERN.matcher(line);
            if (dayMatcher.find() && line.contains("每周")) {
                return Integer.parseInt(dayMatcher.group(1)) * 2;
            }
            return null;
        }

        private Integer extractQuota(String line) {
            Matcher matcher = QUOTA_PATTERN.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            return value == null ? null : Integer.parseInt(value);
        }

        private Integer extractFirst(String line, Pattern pattern) {
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                return null;
            }
            return Integer.parseInt(matcher.group(1));
        }

        private boolean skipDay(int dayIndex) {
            return weekendOff && dayIndex >= 5;
        }

        private int normalQuota(int fallback) {
            return normalQuota == null ? fallback : normalQuota;
        }

        private int expertQuota(int fallback) {
            return expertQuota == null ? fallback : expertQuota;
        }

        private List<String> warnings() {
            return warnings;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
