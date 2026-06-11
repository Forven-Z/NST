package com.hospital.management.service;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.client.AuthStaffFeignClient;
import com.hospital.management.client.dto.StaffAccountCreateFeignRequest;
import com.hospital.management.client.dto.StaffAccountUpdateFeignRequest;
import com.hospital.management.dto.EmployeeWriteRequest;
import com.hospital.management.repository.DepartmentRepository;
import com.hospital.management.repository.EmployeeRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final Map<String, String> ROLE_TYPE_LABELS = Map.of(
            "OUTPATIENT_DOCTOR", "门诊医生",
            "CHECK_DOCTOR", "检查医生",
            "LAB_DOCTOR", "检验医生",
            "DISPOSAL_DOCTOR", "处置医生",
            "PHARMACIST", "药师",
            "REGISTRAR", "挂号收费员",
            "ADMIN", "管理员"
    );

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthStaffFeignClient authStaffFeignClient;
    private final EmployeePersistenceService employeePersistenceService;

    public Map<String, Object> list(String keyword, Long deptId, String roleType, Integer delmark,
                                    Integer scheduleKind, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        return Map.of(
                "list", employeeRepository.listEmployees(keyword, deptId, roleType, delmark, scheduleKind, offset, pageSize)
                        .stream().map(this::enrich).toList(),
                "page", page,
                "pageSize", pageSize
        );
    }

    public Map<String, Object> getById(Long id) {
        return enrich(employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "员工不存在")));
    }

    @Transactional
    public Map<String, Object> create(EmployeeWriteRequest req) {
        long employeeId = employeePersistenceService.insertEmployee(req);
        try {
            Result<Map<String, Object>> authResult = authStaffFeignClient.createAccount(
                    new StaffAccountCreateFeignRequest(
                            employeeId, req.getUsername().trim(),
                            req.getPassword() != null ? req.getPassword() : "123456",
                            req.getRoleType()));
            if (authResult == null || authResult.getCode() != 200) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        authResult != null ? authResult.getMessage() : "账号服务不可用");
            }
        } catch (FeignException | BusinessException ex) {
            employeeRepository.deleteById(employeeId);
            if (ex instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号服务不可用");
        }
        Map<String, Object> row = enrich(employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "员工不存在")));
        row.put("message", "员工已建档，可使用 " + req.getUsername().trim() + " / "
                + (req.getPassword() != null ? req.getPassword() : "123456") + " 登录");
        return row;
    }

    public Map<String, Object> update(Long id, EmployeeWriteRequest req) {
        if (!employeeRepository.existsActive(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        if (req.getDeptId() != null && !departmentRepository.existsActive(req.getDeptId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "科室不存在");
        }
        try {
            employeeRepository.update(id,
                    StringUtils.hasText(req.getEmpNo()) ? req.getEmpNo().trim() : null,
                    StringUtils.hasText(req.getRealName()) ? req.getRealName().trim() : null,
                    req.getGender(),
                    req.getDeptId(),
                    req.getTitle(),
                    StringUtils.hasText(req.getRoleType()) ? req.getRoleType() : null,
                    req.getPhone());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工号已存在");
        }
        if (StringUtils.hasText(req.getPassword())) {
            try {
                Result<Map<String, Object>> authResult = authStaffFeignClient.updateAccount(id,
                        new StaffAccountUpdateFeignRequest(null, req.getPassword(), null));
                if (authResult == null || authResult.getCode() != 200) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            authResult != null ? authResult.getMessage() : "账号服务不可用");
                }
            } catch (FeignException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "账号服务不可用");
            }
        }
        Map<String, Object> row = enrich(employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "员工不存在")));
        row.put("message", "员工信息已更新");
        return row;
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        if (!employeeRepository.existsActive(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        Result<Map<String, Object>> authResult;
        try {
            authResult = authStaffFeignClient.disableAccount(id);
        } catch (FeignException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号服务不可用");
        }
        if (authResult == null || authResult.getCode() != 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    authResult != null ? authResult.getMessage() : "账号服务不可用");
        }
        if (employeeRepository.softDelete(id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        Map<String, Object> row = enrich(employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "员工不存在")));
        row.put("message", "员工已停用，登录已关闭");
        return row;
    }

    private Map<String, Object> enrich(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);
        String roleType = (String) row.get("roleType");
        enriched.put("roleTypeLabel", ROLE_TYPE_LABELS.getOrDefault(roleType, roleType));
        enriched.put("hasAccount", row.get("username") != null);
        Integer delmark = (Integer) row.get("delmark");
        Integer accountStatus = (Integer) row.get("accountStatus");
        enriched.put("accountStatus", delmark != null && delmark == 1 ? 0 : (accountStatus != null ? accountStatus : 1));
        return enriched;
    }
}
