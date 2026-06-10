package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.DepartmentRepository;
import com.hospital.his.repository.EmployeeRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.repository.SettleCategoryRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrarQueryService {

    private static final int EXPERT_SESSION_LOOKAHEAD_DAYS = 7;

    private final PatientRepository patientRepository;
    private final BillRepository billRepository;
    private final DepartmentRepository departmentRepository;
    private final SettleCategoryRepository settleCategoryRepository;
    private final EmployeeRepository employeeRepository;
    private final SchedulingService schedulingService;

    public Map<String, Object> listOutpatientDepartments() {
        requireRegistrar();
        return Map.of(
                "list", departmentRepository.listOutpatientDepartments(),
                "page", 1,
                "pageSize", 50
        );
    }

    public Map<String, Object> listSettleCategories() {
        requireRegistrar();
        return Map.of(
                "list", settleCategoryRepository.listAll(),
                "page", 1,
                "pageSize", 20
        );
    }

    public Map<String, Object> listDoctorsByDept(Long deptId) {
        requireRegistrar();
        if (deptId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "deptId 不能为空");
        }
        List<Map<String, Object>> list = employeeRepository.listOutpatientDoctorsByDept(deptId).stream()
                .map(this::enrichDoctorRow)
                .toList();
        return Map.of("list", list);
    }

    public Map<String, Object> listSchedules(Long deptId, Long employeeId, Long registLevelId, LocalDate workDate) {
        requireRegistrar();
        return schedulingService.listRegistrarSchedules(deptId, employeeId, registLevelId, workDate);
    }

    public Map<String, Object> listBillsByMedicalRecordNo(String medicalRecordNo, Integer status) {
        requireRegistrar();
        Long patientId = patientRepository.findPatientIdByMedicalRecordNo(medicalRecordNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "病历号不存在"));
        List<Map<String, Object>> list = billRepository.findByPatientIdForDisplay(patientId, status);
        return Map.of(
                "medicalRecordNo", medicalRecordNo,
                "patientId", patientId,
                "list", list
        );
    }

    private Map<String, Object> enrichDoctorRow(Map<String, Object> row) {
        Map<String, Object> enriched = new HashMap<>(row);
        String title = (String) row.get("title");
        boolean expert = isExpertTitle(title);
        enriched.put("clinicRole", expert ? "EXPERT" : "REGULAR");
        enriched.put("role", expert ? "expert" : "regular");
        if (expert) {
            Long employeeId = ((Number) row.get("employeeId")).longValue();
            enriched.put("expertSessionCount",
                    employeeRepository.countExpertSessions(employeeId, EXPERT_SESSION_LOOKAHEAD_DAYS));
        }
        return enriched;
    }

    private boolean isExpertTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        return title.contains("主任") || title.contains("教授");
    }

    private void requireRegistrar() {
        var context = AuthContextHolder.require();
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
    }
}
