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
import java.time.Period;
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
        return buildBillResult(patientId, status);
    }

    /**
     * 收费窗口查账：病历号、身份证号均为精确匹配；姓名为精确匹配，重名返回候选列表。
     * 多条件同时填写时优先级：病历号 &gt; 身份证号 &gt; 姓名 &gt; patientId。
     */
    public Map<String, Object> listBillsByQuery(String medicalRecordNo, String idCard, String realName,
                                                Long patientId, Integer status) {
        requireRegistrar();
        if (StringUtils.hasText(medicalRecordNo)) {
            return listBillsByMedicalRecordNo(medicalRecordNo.trim(), status);
        }
        if (StringUtils.hasText(idCard)) {
            Long resolvedId = patientRepository.findPatientIdByIdCard(idCard)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "身份证号不存在"));
            return buildBillResult(resolvedId, status);
        }
        if (StringUtils.hasText(realName)) {
            List<Map<String, Object>> candidates = patientRepository.listPatientSummariesByRealName(realName, 20);
            if (candidates.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该姓名患者");
            }
            if (candidates.size() > 1) {
                Map<String, Object> result = new HashMap<>();
                result.put("multiple", true);
                result.put("candidates", candidates.stream().map(this::maskCandidate).toList());
                return result;
            }
            Long resolvedId = ((Number) candidates.get(0).get("patientId")).longValue();
            return buildBillResult(resolvedId, status);
        }
        if (patientId != null) {
            if (patientRepository.findMedicalRecordNo(patientId) == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "患者不存在");
            }
            return buildBillResult(patientId, status);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供病历号、身份证号或姓名");
    }

    private Map<String, Object> buildBillResult(Long patientId, Integer status) {
        var profile = patientRepository.findProfileById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "患者不存在"));
        List<Map<String, Object>> list = billRepository.findByPatientIdForDisplay(patientId, status);
        Map<String, Object> result = new HashMap<>();
        result.put("multiple", false);
        result.put("medicalRecordNo", profile.getMedicalRecordNo());
        result.put("patientId", patientId);
        result.put("realName", profile.getRealName());
        result.put("gender", profile.getGender());
        if (profile.getBirthDate() != null) {
            result.put("age", Period.between(profile.getBirthDate(), LocalDate.now()).getYears());
        }
        result.put("list", list);
        return result;
    }

    private Map<String, Object> maskCandidate(Map<String, Object> row) {
        Map<String, Object> masked = new HashMap<>(row);
        Object idCard = row.get("idCard");
        if (idCard instanceof String s && s.length() >= 8) {
            masked.put("idCard", s.substring(0, 4) + "**********" + s.substring(s.length() - 4));
        }
        Object birthDate = row.get("birthDate");
        if (birthDate instanceof LocalDate bd) {
            masked.put("age", Period.between(bd, LocalDate.now()).getYears());
        }
        return masked;
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
