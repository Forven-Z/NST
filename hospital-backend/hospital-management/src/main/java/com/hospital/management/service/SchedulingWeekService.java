package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.ApplyTemplateRequest;
import com.hospital.management.dto.BatchPublishRequest;
import com.hospital.management.dto.BatchUpsertChangeItem;
import com.hospital.management.dto.BatchUpsertRequest;
import com.hospital.management.dto.CopyWeekRequest;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.SchedulingRepository;
import com.hospital.management.repository.SchedulingTemplateRepository;
import com.hospital.management.support.NoonTypeSupport;
import com.hospital.management.support.RegistLevelQuotaDefaults;
import com.hospital.management.support.WeekStartSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SchedulingWeekService {

    private final SchedulingRepository schedulingRepository;
    private final SchedulingTemplateRepository templateRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestService leaveRequestService;

    public Map<String, Object> getWeekGrid(Long deptId, LocalDate weekStart) {
        LocalDate alignedStart = WeekStartSupport.alignToMonday(weekStart);
        LocalDate weekEnd = WeekStartSupport.weekEnd(alignedStart);
        boolean prefilledFromTemplate = false;

        List<Map<String, Object>> slots = loadEnrichedSlots(deptId, alignedStart, weekEnd);
        LocalDate thisMonday = WeekStartSupport.alignToMonday(LocalDate.now());
        if (!alignedStart.isBefore(thisMonday) && slots.isEmpty()
                && templateRepository.hasEnabledForDept(deptId)) {
            fillFromTemplate(deptId, alignedStart, null);
            slots = loadEnrichedSlots(deptId, alignedStart, weekEnd);
            prefilledFromTemplate = true;
        }

        List<Map<String, Object>> doctors = employeeRepository
                .listEmployees(null, deptId, null, 0, 1, 0, 200)
                .stream()
                .map(emp -> {
                    Map<String, Object> doctor = new LinkedHashMap<>();
                    doctor.put("employeeId", emp.get("employeeId"));
                    doctor.put("realName", emp.get("realName"));
                    doctor.put("title", emp.get("title"));
                    return doctor;
                })
                .toList();

        int draftCount = 0;
        int publishedCount = 0;
        for (Map<String, Object> slot : slots) {
            int status = (Integer) slot.get("publishStatus");
            if (status == 0) {
                draftCount++;
            } else if (status == 1) {
                publishedCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weekStart", alignedStart);
        result.put("weekEnd", weekEnd);
        result.put("deptId", deptId);
        result.put("doctors", doctors);
        result.put("slots", slots);
        result.put("prefilledFromTemplate", prefilledFromTemplate);
        result.put("draftCount", draftCount);
        result.put("publishedCount", publishedCount);
        return result;
    }

    @Transactional
    public Map<String, Object> batchUpsert(BatchUpsertRequest request) {
        int created = 0;
        int updated = 0;
        int cleared = 0;

        for (BatchUpsertChangeItem change : request.getChanges()) {
            if (Boolean.TRUE.equals(change.getClear())) {
                cleared += clearSlot(change.getSchedulingId());
                continue;
            }
            if (change.getSchedulingId() != null) {
                updated += updateSlot(change);
                continue;
            }
            created += createSlot(change);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("cleared", cleared);
        result.put("message", "排班已保存");
        return result;
    }

    @Transactional
    public Map<String, Object> copyWeek(CopyWeekRequest request) {
        LocalDate sourceStart = WeekStartSupport.alignToMonday(request.getSourceWeekStart());
        LocalDate sourceEnd = WeekStartSupport.weekEnd(sourceStart);
        LocalDate targetStart = WeekStartSupport.alignToMonday(request.getTargetWeekStart());

        List<Map<String, Object>> sourceSlots = schedulingRepository.listWeekByDept(
                request.getDeptId(), sourceStart, sourceEnd);
        List<FillItem> items = sourceSlots.stream()
                .map(slot -> new FillItem(
                        (Long) slot.get("employeeId"),
                        WeekStartSupport.toWeekday((LocalDate) slot.get("workDate")),
                        (Integer) slot.get("noonType"),
                        (Long) slot.get("registLevelId"),
                        (Integer) slot.get("totalQuota")))
                .toList();

        FillResult fillResult = fillSlots(targetStart, items);
        return buildFillResponse(fillResult, "复制上周完成");
    }

    @Transactional
    public Map<String, Object> applyTemplate(ApplyTemplateRequest request) {
        LocalDate weekStart = WeekStartSupport.alignToMonday(request.getWeekStart());
        List<Long> employeeIds = resolveEmployeeIds(request.getDeptId(), request.getEmployeeIds());
        FillResult fillResult = fillFromTemplate(request.getDeptId(), weekStart, employeeIds);
        return buildFillResponse(fillResult, "模板已应用");
    }

    @Transactional
    public Map<String, Object> batchPublish(BatchPublishRequest request) {
        LocalDate weekStart = WeekStartSupport.alignToMonday(request.getWeekStart());
        LocalDate weekEnd = WeekStartSupport.weekEnd(weekStart);
        int published = schedulingRepository.batchPublishWeek(request.getDeptId(), weekStart, weekEnd);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("published", published);
        result.put("message", "已发布 " + published + " 条排班");
        return result;
    }

    private FillResult fillFromTemplate(Long deptId, LocalDate weekStart, List<Long> employeeIds) {
        List<Long> ids = resolveEmployeeIds(deptId, employeeIds);
        List<Map<String, Object>> templates = templateRepository.listEnabledForEmployees(ids);
        List<FillItem> items = templates.stream()
                .map(t -> new FillItem(
                        (Long) t.get("employeeId"),
                        (Integer) t.get("weekday"),
                        (Integer) t.get("noonType"),
                        (Long) t.get("registLevelId"),
                        (Integer) t.get("totalQuota")))
                .toList();
        return fillSlots(weekStart, items);
    }

    private List<Long> resolveEmployeeIds(Long deptId, List<Long> employeeIds) {
        if (employeeIds != null && !employeeIds.isEmpty()) {
            return employeeIds;
        }
        return employeeRepository.listEmployees(null, deptId, null, 0, 1, 0, 200)
                .stream()
                .map(emp -> (Long) emp.get("employeeId"))
                .toList();
    }

    private FillResult fillSlots(LocalDate weekStart, List<FillItem> items) {
        int created = 0;
        int skipped = 0;
        LocalDate today = LocalDate.now();

        for (FillItem item : items) {
            LocalDate workDate = WeekStartSupport.dateForWeekday(weekStart, item.weekday());
            if (workDate.isBefore(today)) {
                skipped++;
                continue;
            }
            if (schedulingRepository.existsActiveSlot(item.employeeId(), workDate, item.noonType())) {
                skipped++;
                continue;
            }
            int quota = item.totalQuota() != null && item.totalQuota() > 0
                    ? item.totalQuota()
                    : RegistLevelQuotaDefaults.defaultQuota(item.registLevelId());
            try {
                schedulingRepository.insert(
                        item.employeeId(), item.registLevelId(), workDate, item.noonType(), quota);
                created++;
            } catch (DataIntegrityViolationException ex) {
                skipped++;
            }
        }
        return new FillResult(created, skipped);
    }

    private int createSlot(BatchUpsertChangeItem change) {
        if (change.getEmployeeId() == null || change.getWorkDate() == null || change.getNoonType() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新建排班缺少必要字段");
        }
        if (change.getWorkDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出诊日期不能早于今天");
        }
        validateOutpatientDoctor(change.getEmployeeId());
        if (schedulingRepository.existsActiveSlot(
                change.getEmployeeId(), change.getWorkDate(), change.getNoonType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该医生此时段已有排班");
        }
        if (change.getRegistLevelId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择号别");
        }
        int quota = resolveQuota(change.getRegistLevelId(), change.getTotalQuota());
        try {
            schedulingRepository.insert(
                    change.getEmployeeId(),
                    change.getRegistLevelId(),
                    change.getWorkDate(),
                    change.getNoonType(),
                    quota);
            return 1;
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该医生此时段已有排班");
        }
    }

    private int updateSlot(BatchUpsertChangeItem change) {
        Map<String, Object> current = loadAndEnrich(change.getSchedulingId());
        rejectIfLeaveLocked(current, change.getRegistLevelId() != null);

        int publishStatus = (Integer) current.get("publishStatus");
        int usedQuota = (Integer) current.get("usedQuota");
        Long currentLevelId = (Long) current.get("registLevelId");

        if (publishStatus == 0) {
            if (change.getRegistLevelId() != null && !change.getRegistLevelId().equals(currentLevelId)) {
                if (schedulingRepository.updateDraft(
                        change.getSchedulingId(), change.getRegistLevelId(), null) == 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿排班可修改号别");
                }
            }
            Integer quota = change.getTotalQuota();
            if (quota != null && quota < usedQuota) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "总号源不能小于已用号源");
            }
            if (schedulingRepository.updateDraft(change.getSchedulingId(), null, quota) == 0 && quota != null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在");
            }
            return 1;
        }

        if (publishStatus == 1) {
            if (change.getRegistLevelId() != null && !change.getRegistLevelId().equals(currentLevelId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布排班不可修改号别");
            }
            Integer quota = change.getTotalQuota();
            if (quota == null) {
                return 0;
            }
            if (quota < usedQuota) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "总号源不能小于已用号源");
            }
            if (schedulingRepository.updatePublishedQuota(change.getSchedulingId(), quota) == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已发布排班号源仅可增加");
            }
            return 1;
        }

        throw new BusinessException(ErrorCode.BAD_REQUEST, "已取消排班不可修改");
    }

    private int clearSlot(Long schedulingId) {
        Map<String, Object> current = loadAndEnrich(schedulingId);
        rejectIfLeaveLocked(current, true);
        if ((Integer) current.get("publishStatus") != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿排班可清除");
        }
        if ((Integer) current.get("usedQuota") > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已有挂号记录，不可作废");
        }
        if (schedulingRepository.cancelDraft(schedulingId) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "清除排班失败");
        }
        return 1;
    }

    private Map<String, Object> loadAndEnrich(Long schedulingId) {
        Map<String, Object> row = schedulingRepository.findByIdForUpdate(schedulingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在"));
        return leaveRequestService.enrichScheduleRow(enrich(row));
    }

    private void rejectIfLeaveLocked(Map<String, Object> row, boolean modifyingLevelOrClear) {
        if (!modifyingLevelOrClear) {
            return;
        }
        if (Boolean.TRUE.equals(row.get("pendingLeave")) || Boolean.TRUE.equals(row.get("needsSubstitute"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该班次有请假流程进行中，不可修改");
        }
    }

    private List<Map<String, Object>> loadEnrichedSlots(Long deptId, LocalDate weekStart, LocalDate weekEnd) {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (Map<String, Object> row : schedulingRepository.listWeekByDept(deptId, weekStart, weekEnd)) {
            Map<String, Object> enriched = leaveRequestService.enrichScheduleRow(enrich(row));
            LocalDate workDate = (LocalDate) enriched.get("workDate");
            enriched.put("weekday", WeekStartSupport.toWeekday(workDate));
            slots.add(enriched);
        }
        return slots;
    }

    private Map<String, Object> enrich(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);
        int noonType = (Integer) row.get("noonType");
        int totalQuota = (Integer) row.get("totalQuota");
        int usedQuota = (Integer) row.get("usedQuota");
        enriched.put("scheduleKind", 1);
        enriched.put("noonLabel", NoonTypeSupport.label(noonType));
        enriched.put("timeRange", NoonTypeSupport.timeRange(noonType));
        enriched.put("remainQuota", totalQuota - usedQuota);
        return enriched;
    }

    private int resolveQuota(Long registLevelId, Integer totalQuota) {
        if (totalQuota != null && totalQuota > 0) {
            return totalQuota;
        }
        return RegistLevelQuotaDefaults.defaultQuota(registLevelId);
    }

    private void validateOutpatientDoctor(Long employeeId) {
        if (!employeeRepository.existsActive(employeeId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "员工不存在或已停用");
        }
        String roleType = employeeRepository.findRoleType(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "员工不存在或已停用"));
        if (!"OUTPATIENT_DOCTOR".equals(roleType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅门诊医生可排班");
        }
    }

    private Map<String, Object> buildFillResponse(FillResult fillResult, String prefix) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", fillResult.created());
        result.put("skipped", fillResult.skipped());
        result.put("message", prefix + "：新建 " + fillResult.created() + " 条，跳过 " + fillResult.skipped() + " 条");
        return result;
    }

    private record FillItem(Long employeeId, int weekday, int noonType, Long registLevelId, Integer totalQuota) {
    }

    private record FillResult(int created, int skipped) {
    }
}
