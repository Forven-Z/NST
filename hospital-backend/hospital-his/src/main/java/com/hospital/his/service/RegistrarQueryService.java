package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.DepartmentRepository;
import com.hospital.his.repository.EmployeeRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.repository.SettleCategoryRepository;
import com.hospital.his.security.AuthContextHolder;
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
    private static final int REGISTRAR_REGISTER_LIST_LIMIT = 50;

    private final PatientRepository patientRepository;
    private final BillRepository billRepository;
    private final RegisterRepository registerRepository;
    private final DepartmentRepository departmentRepository;
    private final SettleCategoryRepository settleCategoryRepository;
    private final EmployeeRepository employeeRepository;
    private final SchedulingService schedulingService;
    private final FinancialQueryService financialQueryService;
    private final RegisterLifecycleService registerLifecycleService;

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
        Map<String, Object> early = new HashMap<>();
        Long resolvedId = resolvePatientId(medicalRecordNo, idCard, realName, patientId, early);
        if (resolvedId == null) {
            return early;
        }
        return buildBillResult(resolvedId, status);
    }

    /**
     * 收费窗口查挂号：病历号、身份证号均为精确匹配；姓名为精确匹配，重名返回候选列表。
     */
    public Map<String, Object> listRegistersByQuery(String medicalRecordNo, String idCard, String realName,
                                                    Long patientId, Integer visitState) {
        requireRegistrar();
        Map<String, Object> early = new HashMap<>();
        Long resolvedId = resolvePatientId(medicalRecordNo, idCard, realName, patientId, early);
        if (resolvedId == null) {
            return early;
        }
        return buildRegisterResult(resolvedId, visitState);
    }

    public Map<String, Object> listPaymentsByQuery(String medicalRecordNo, String idCard, String realName,
                                                   Long patientId, Long registerId, int page, int pageSize) {
        requireRegistrar();
        Map<String, Object> early = new HashMap<>();
        Long resolvedId = resolvePatientId(medicalRecordNo, idCard, realName, patientId, early);
        if (resolvedId == null) {
            return early;
        }
        return buildPatientFinanceResult(resolvedId, financialQueryService.listPayments(resolvedId, registerId, page, pageSize));
    }

    public Map<String, Object> listRefundsByQuery(String medicalRecordNo, String idCard, String realName,
                                                  Long patientId, Long registerId, int page, int pageSize) {
        requireRegistrar();
        Map<String, Object> early = new HashMap<>();
        Long resolvedId = resolvePatientId(medicalRecordNo, idCard, realName, patientId, early);
        if (resolvedId == null) {
            return early;
        }
        return buildPatientFinanceResult(resolvedId, financialQueryService.listRefunds(resolvedId, registerId, page, pageSize));
    }

    public Map<String, Object> shiftSummary(LocalDate workDate) {
        requireRegistrar();
        Long operatorId = AuthContextHolder.require().getEmployeeId();
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未绑定员工信息");
        }
        return financialQueryService.shiftSummary(operatorId, workDate);
    }

    private Map<String, Object> buildPatientFinanceResult(Long patientId, Map<String, Object> financeData) {
        var profile = patientRepository.findProfileById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "患者不存在"));
        Map<String, Object> result = new HashMap<>(financeData);
        result.put("multiple", false);
        result.put("medicalRecordNo", profile.getMedicalRecordNo());
        result.put("patientId", patientId);
        result.put("realName", profile.getRealName());
        result.put("gender", profile.getGender());
        if (profile.getBirthDate() != null) {
            result.put("age", Period.between(profile.getBirthDate(), LocalDate.now()).getYears());
        }
        return result;
    }

    /**
     * @return patientId；若 early 已写入 multiple/candidates 则返回 null
     */
    private Long resolvePatientId(String medicalRecordNo, String idCard, String realName, Long patientId,
                                  Map<String, Object> early) {
        if (StringUtils.hasText(medicalRecordNo)) {
            return patientRepository.findPatientIdByMedicalRecordNo(medicalRecordNo.trim())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "病历号不存在"));
        }
        if (StringUtils.hasText(idCard)) {
            return patientRepository.findPatientIdByIdCard(idCard)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "身份证号不存在"));
        }
        if (StringUtils.hasText(realName)) {
            List<Map<String, Object>> candidates = patientRepository.listPatientSummariesByRealName(realName, 20);
            if (candidates.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该姓名患者");
            }
            if (candidates.size() > 1) {
                early.put("multiple", true);
                early.put("candidates", candidates.stream().map(this::maskCandidate).toList());
                return null;
            }
            return ((Number) candidates.get(0).get("patientId")).longValue();
        }
        if (patientId != null) {
            if (patientRepository.findMedicalRecordNo(patientId) == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "患者不存在");
            }
            return patientId;
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

    private Map<String, Object> buildRegisterResult(Long patientId, Integer visitState) {
        var profile = patientRepository.findProfileById(patientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "患者不存在"));
        List<Map<String, Object>> list = registerRepository
                .findByPatientIdForRegistrar(patientId, visitState, REGISTRAR_REGISTER_LIST_LIMIT)
                .stream()
                .map(this::enrichRegisterRow)
                .toList();
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

    private Map<String, Object> enrichRegisterRow(Map<String, Object> row) {
        Map<String, Object> enriched = registerLifecycleService.enrichRegisterRow(row);
        int visitState = ((Number) row.get("visitState")).intValue();
        enriched.put("visitStateLabel", visitStateLabel(visitState));
        return enriched;
    }

    private static String visitStateLabel(int visitState) {
        return switch (visitState) {
            case VisitState.PENDING_PAYMENT -> "待支付";
            case VisitState.REGISTERED -> "已挂号";
            case VisitState.IN_CONSULTATION -> "接诊中";
            case VisitState.FINISHED -> "看诊结束";
            case VisitState.CANCELLED -> "已退号";
            default -> "未知";
        };
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
