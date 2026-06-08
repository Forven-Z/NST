package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientRegisterQueryService {

    private final RegisterRepository registerRepository;
    private final PatientFamilyService patientFamilyService;

    public Map<String, Object> listRegisters(Integer visitState, int page, int pageSize, Long visitPatientId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        Long visitId = patientFamilyService.resolveVisitPatientId(visitPatientId);
        int offset = Math.max(page - 1, 0) * pageSize;
        List<Map<String, Object>> list = registerRepository.findByVisitPatientForOperator(
                operatorId, visitId, visitState, offset, pageSize);
        return Map.of("list", list, "page", page, "pageSize", pageSize, "visitPatientId", visitId);
    }

    public Map<String, Object> getRegister(Long registerId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        return registerRepository.findDetailForOwner(registerId, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
    }

    public Map<String, Object> getQueueStatus(Long registerId) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        Map<String, Object> reg = registerRepository.findDetailForOwner(registerId, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        int visitState = ((Number) reg.get("visitState")).intValue();
        int ahead = 0;
        String hint = "请先完成缴费";
        if (visitState == VisitState.REGISTERED) {
            ahead = registerRepository.countAheadInQueue(registerId);
            hint = ahead == 0 ? "即将轮到您，请至诊室候诊" : "前面还有 " + ahead + " 人，请留意叫号";
        } else if (visitState == VisitState.IN_CONSULTATION) {
            hint = "医生正在接诊";
        } else if (visitState == VisitState.PENDING_PAYMENT) {
            hint = "待支付挂号费";
        } else if (visitState == VisitState.FINISHED) {
            hint = "看诊已结束";
        }
        Map<String, Object> result = new HashMap<>(reg);
        result.put("aheadCount", ahead);
        result.put("queueHint", hint);
        return result;
    }
}
