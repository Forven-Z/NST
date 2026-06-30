package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.TemplateReplaceRequest;
import com.hospital.management.dto.TemplateSlotItem;
import com.hospital.management.repository.EmployeeRepository;
import com.hospital.management.repository.SchedulingTemplateRepository;
import com.hospital.management.support.RegistLevelQuotaDefaults;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchedulingTemplateService {

    private final SchedulingTemplateRepository templateRepository;
    private final EmployeeRepository employeeRepository;

    public Map<String, Object> getTemplate(Long employeeId) {
        if (!employeeRepository.existsActive(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在或已停用");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeeId", employeeId);
        result.put("slots", templateRepository.listByEmployee(employeeId));
        return result;
    }

    public Map<String, Object> replaceTemplate(Long employeeId, TemplateReplaceRequest req) {
        validateOutpatientDoctor(employeeId);
        List<Map<String, Object>> slots = new ArrayList<>();
        for (TemplateSlotItem item : req.getSlots()) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("weekday", item.getWeekday());
            slot.put("noonType", item.getNoonType());
            slot.put("registLevelId", item.getRegistLevelId());
            slot.put("totalQuota", item.getTotalQuota() != null
                    ? item.getTotalQuota()
                    : RegistLevelQuotaDefaults.defaultQuota(item.getRegistLevelId()));
            slot.put("enabled", item.getEnabled() != null ? item.getEnabled() : Boolean.TRUE);
            slots.add(slot);
        }
        templateRepository.replaceForEmployee(employeeId, slots);
        Map<String, Object> result = new LinkedHashMap<>(getTemplate(employeeId));
        result.put("message", "固定模板已保存");
        return result;
    }

    public Map<String, Object> listTemplates(Long deptId, Long employeeId) {
        if (employeeId != null) {
            return getTemplate(employeeId);
        }
        List<Map<String, Object>> list = employeeRepository
                .listEmployees(null, deptId, null, 0, 1, 0, 200)
                .stream()
                .map(emp -> {
                    Long empId = (Long) emp.get("employeeId");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("employeeId", empId);
                    row.put("realName", emp.get("realName"));
                    row.put("title", emp.get("title"));
                    row.put("slotCount", templateRepository.listByEmployee(empId).size());
                    return row;
                })
                .toList();
        return Map.of("list", list);
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
}
