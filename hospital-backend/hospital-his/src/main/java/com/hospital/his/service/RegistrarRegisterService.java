package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.RegisterChannel;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.registrar.WindowRegisterRequest;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.PatientRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.repository.SchedulingRepository;
import com.hospital.his.security.AuthContext;
import com.hospital.his.security.AuthContextHolder;
import com.hospital.his.util.IdCardUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrarRegisterService {

    private static final BigDecimal RECORD_BOOK_FEE = new BigDecimal("1.00");

    private final PatientLoginPersistence patientLoginPersistence;
    private final PatientRepository patientRepository;
    private final SchedulingRepository schedulingRepository;
    private final RegisterRepository registerRepository;
    private final BillRepository billRepository;

    @Transactional
    public Map<String, Object> windowRegister(WindowRegisterRequest request) {
        Long registrarId = requireRegistrar();

        Long settleCategoryId = request.getSettleCategoryId() != null ? request.getSettleCategoryId() : 1L;
        LocalDate birthDate = IdCardUtils.resolveBirthDate(
                request.getBirthDate(), request.getAge(), request.getIdCard());
        Integer age = IdCardUtils.resolveAge(request.getAge(), birthDate);
        PatientLoginPersistence.UpsertResult upsert = patientLoginPersistence.upsertForWindow(
                request.getPatientName(),
                request.getGender(),
                birthDate,
                age,
                request.getPhone(),
                request.getIdCard(),
                request.getAddress(),
                settleCategoryId);
        Long patientId = upsert.patientId();

        Map<String, Object> scheduling = schedulingRepository.findByIdForUpdate(request.getSchedulingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "排班不存在或已停诊"));

        int remain = toInt(scheduling.get("totalQuota")) - toInt(scheduling.get("usedQuota"));
        if (remain <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "号源已满");
        }

        if (!equalsLong(scheduling.get("deptId"), request.getDeptId())
                || !equalsLong(scheduling.get("employeeId"), request.getEmployeeId())
                || !equalsLong(scheduling.get("registLevelId"), request.getRegistLevelId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "排班信息与请求不一致");
        }

        BigDecimal registFee = (BigDecimal) scheduling.get("registFee");
        LocalDate visitDate = (LocalDate) scheduling.get("workDate");
        int noonType = toInt(scheduling.get("noonType"));
        boolean needRecordBook = Boolean.TRUE.equals(request.getNeedRecordBook());

        if (registerRepository.existsActiveRegister(
                patientId, request.getEmployeeId(), visitDate, noonType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该患者今日已在该医生该午别挂号，请勿重复提交");
        }

        long registerId = registerRepository.insertRegister(
                patientId,
                request.getSchedulingId(),
                request.getDeptId(),
                request.getEmployeeId(),
                request.getRegistLevelId(),
                settleCategoryId,
                visitDate,
                noonType,
                VisitState.PENDING_PAYMENT,
                registFee,
                RegisterChannel.WINDOW,
                registrarId);

        schedulingRepository.incrementUsedQuota(request.getSchedulingId());

        BigDecimal billAmount = registFee;
        String billTitle = "挂号费";
        if (needRecordBook) {
            billAmount = billAmount.add(RECORD_BOOK_FEE);
            billTitle = "挂号费（含病历本）";
        }

        List<Long> billIds = new ArrayList<>();
        billIds.add(billRepository.insertBill(
                patientId, registerId, BillBizType.REGISTER, registerId, billTitle, billAmount));

        BigDecimal totalAmount = billAmount;

        String medicalRecordNo = patientRepository.findMedicalRecordNo(patientId);

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);
        result.put("patientId", patientId);
        result.put("medicalRecordNo", medicalRecordNo);
        result.put("patientName", request.getPatientName());
        result.put("billIds", billIds);
        result.put("billId", billIds.isEmpty() ? null : billIds.get(0));
        result.put("amount", totalAmount);
        result.put("visitState", VisitState.PENDING_PAYMENT);
        result.put("deptName", scheduling.get("deptName"));
        result.put("doctorName", scheduling.get("doctorName"));
        result.put("workDate", visitDate);
        result.put("noonLabel", noonType == 1 ? "上午" : "下午");
        result.put("registLevelName", scheduling.get("levelName"));
        result.put("message", "挂号成功，请至收费窗口缴纳挂号费后进入「已挂号」状态");
        return result;
    }

    private Long requireRegistrar() {
        AuthContext context = AuthContextHolder.require();
        if (!context.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要收费员身份");
        }
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
        return context.getEmployeeId();
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private boolean equalsLong(Object left, Long right) {
        if (left == null || right == null) {
            return false;
        }
        return ((Number) left).longValue() == right;
    }
}
