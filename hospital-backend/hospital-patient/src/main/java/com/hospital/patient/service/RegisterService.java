package com.hospital.patient.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.dto.patient.CreateRegisterRequest;
import com.hospital.patient.repository.BillRepository;
import com.hospital.patient.repository.RegisterRepository;
import com.hospital.patient.repository.SchedulingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegisterService {

    private final SchedulingRepository schedulingRepository;
    private final RegisterRepository registerRepository;
    private final BillRepository billRepository;
    private final PatientFamilyService patientFamilyService;

    @Transactional
    public Map<String, Object> createRegister(CreateRegisterRequest request) {
        Long patientId = patientFamilyService.resolveVisitPatientId(request.getMemberPatientId());

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

        if (registerRepository.existsActiveRegister(
                patientId, request.getEmployeeId(), visitDate, noonType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该就诊人今日已在该医生该午别挂号，请勿重复提交");
        }

        long registerId = registerRepository.insertRegister(
                patientId,
                request.getSchedulingId(),
                request.getDeptId(),
                request.getEmployeeId(),
                request.getRegistLevelId(),
                request.getSettleCategoryId() != null ? request.getSettleCategoryId() : 1L,
                visitDate,
                noonType,
                VisitState.PENDING_PAYMENT,
                registFee
        );

        schedulingRepository.incrementUsedQuota(request.getSchedulingId());

        long billId = billRepository.insertBill(
                patientId, registerId, BillBizType.REGISTER, registerId, "挂号费", registFee);

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);
        result.put("billId", billId);
        result.put("amount", registFee);
        result.put("visitState", VisitState.PENDING_PAYMENT);
        result.put("message", "请完成支付后进入已挂号状态");
        return result;
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
