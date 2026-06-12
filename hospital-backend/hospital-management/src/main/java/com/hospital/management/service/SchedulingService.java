package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.SchedulingUpdateRequest;
import com.hospital.management.dto.SchedulingWriteRequest;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.SchedulingRepository;
import com.hospital.management.support.NoonTypeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final SchedulingRepository schedulingRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestService leaveRequestService;

    public Map<String, Object> list(Long deptId, Long employeeId, LocalDate workDate, Integer publishStatus) {
        return Map.of(
                "list", schedulingRepository.listAdminSchedules(deptId, employeeId, workDate, publishStatus)
                        .stream()
                        .map(this::enrich)
                        .map(leaveRequestService::enrichScheduleRow)
                        .toList(),
                "page", 1,
                "pageSize", 100
        );
    }

    public Map<String, Object> listMySchedules(Long employeeId, LocalDate workDateFrom) {
        return Map.of(
                "list", schedulingRepository.listMySchedules(employeeId, workDateFrom).stream()
                        .map(this::enrich)
                        .map(leaveRequestService::enrichStaffScheduleRow)
                        .toList(),
                "page", 1,
                "pageSize", 100
        );
    }

    public Map<String, Object> create(SchedulingWriteRequest request) {
        validateOutpatientDoctor(request.getEmployeeId());
        if (request.getWorkDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出诊日期不能早于今天");
        }
        if (request.getTotalQuota() == null || request.getTotalQuota() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "总号源必须大于 0");
        }
        try {
            long id = schedulingRepository.insert(
                    request.getEmployeeId(),
                    request.getRegistLevelId(),
                    request.getWorkDate(),
                    request.getNoonType(),
                    request.getTotalQuota());
            Map<String, Object> row = enrich(schedulingRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在")));
            row.put("message", "排班草稿已创建，请发布");
            return row;
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该医生此时段已有排班");
        }
    }

    public Map<String, Object> update(Long id, SchedulingUpdateRequest request) {
        Map<String, Object> current = schedulingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在"));
        int usedQuota = (Integer) current.get("usedQuota");
        Long currentEmployeeId = (Long) current.get("employeeId");

        if (request.getPublishStatus() != null && request.getPublishStatus() == 2) {
            if (usedQuota > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已有挂号记录，不可作废");
            }
        }

        if (request.getTotalQuota() != null && request.getTotalQuota() < usedQuota) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "总号源不能小于已用号源");
        }

        if (request.getEmployeeId() != null && !request.getEmployeeId().equals(currentEmployeeId)) {
            validateOutpatientDoctor(request.getEmployeeId());
            Long currentDeptId = employeeRepository.findDeptId(currentEmployeeId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "原员工不存在"));
            Long newDeptId = employeeRepository.findDeptId(request.getEmployeeId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "替班员工不存在"));
            if (!currentDeptId.equals(newDeptId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "替班医生须与原医生同科室");
            }
        }

        try {
            if (schedulingRepository.update(id, request.getEmployeeId(), request.getTotalQuota(),
                    request.getPublishStatus()) == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该医生此时段已有排班");
        }

        leaveRequestService.markSubstitutedIfNeeded(id, request.getEmployeeId(), currentEmployeeId);

        Map<String, Object> row = enrich(schedulingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在")));
        row.put("message", request.getEmployeeId() != null && !request.getEmployeeId().equals(currentEmployeeId)
                ? "排班已更新，替班：" + row.get("employeeName")
                : "排班已更新");
        return row;
    }

    public Map<String, Object> publish(Long id) {
        Map<String, Object> current = schedulingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在"));
        if ((Integer) current.get("publishStatus") != 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿排班可发布");
        }
        if (schedulingRepository.publish(id) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿排班可发布");
        }
        Map<String, Object> row = enrich(schedulingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班记录不存在")));
        row.put("message", "排班已发布，患者可挂号");
        return row;
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
}
