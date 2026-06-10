package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.repository.SchedulingRepository;
import com.hospital.his.security.AuthContext;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegisterCancelService {

    private final RegisterRepository registerRepository;
    private final RefundService refundService;
    private final BillRepository billRepository;
    private final SchedulingRepository schedulingRepository;

    @Transactional
    public Map<String, Object> cancelByPatient(Long registerId, String reason) {
        AuthContext context = AuthContextHolder.require();
        if (!context.isPatient()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要患者身份");
        }
        registerRepository.findDetailForOwner(registerId, context.getPatientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState == VisitState.PENDING_PAYMENT) {
            return cancelPendingRegister(registerId, reason);
        }
        if (visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号未接诊状态可退号");
        }
        return doCancel(registerId, reason, null);
    }

    @Transactional
    public Map<String, Object> cancelByRegistrar(Long registerId, String reason) {
        requireRegistrar();
        Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState == VisitState.PENDING_PAYMENT) {
            return cancelPendingRegister(registerId, reason);
        }
        if (visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号未接诊状态可退号");
        }
        return doCancel(registerId, reason, AuthContextHolder.require().getEmployeeId());
    }

    private Map<String, Object> cancelPendingRegister(Long registerId, String reason) {
        Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));

        for (Map<String, Object> bill : billRepository.findPendingByRegisterId(registerId)) {
            billRepository.markVoid(((Number) bill.get("id")).longValue());
        }

        registerRepository.updateVisitState(registerId, VisitState.CANCELLED);

        Object schedulingId = register.get("schedulingId");
        if (schedulingId != null) {
            schedulingRepository.decrementUsedQuota(((Number) schedulingId).longValue());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("registerId", registerId);
        result.put("visitState", VisitState.CANCELLED);
        result.put("message", reason != null ? reason : "待支付挂号已取消");
        return result;
    }

    private Map<String, Object> doCancel(Long registerId, String reason, Long operatorId) {
        String refundReason = reason != null ? reason : "退号";
        Map<String, Object> refundResult = refundService.refundRegisterBill(registerId, refundReason, operatorId);
        Map<String, Object> result = new HashMap<>(refundResult);
        result.put("registerId", registerId);
        result.put("visitState", VisitState.CANCELLED);
        return result;
    }

    private void requireRegistrar() {
        AuthContext context = AuthContextHolder.require();
        if (!context.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要收费员身份");
        }
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
    }
}
