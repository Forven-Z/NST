package com.hospital.his.visit;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.visit.VisitEvent;
import com.hospital.common.visit.VisitTransitionException;
import com.hospital.common.visit.VisitTransitions;
import com.hospital.his.repository.RegisterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 临床域 {@code register.visit_state} 写入口（ADR-019）：叫号 / 结束看诊。
 */
@Service
@RequiredArgsConstructor
public class VisitLifecycleCoordinator {

    private final RegisterRepository registerRepository;

    @Transactional
    public void callPatient(Long registerId) {
        int from = requireCurrentState(registerId);
        VisitTransitions.assertTransition(from, VisitEvent.CALL);
        if (from != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号状态可叫号");
        }
        assertUpdated(registerRepository.markCalledIfCurrent(registerId, from),
                registerId, VisitEvent.CALL, "仅已挂号状态可叫号");
    }

    @Transactional
    public void finishVisit(Long registerId) {
        int from = requireCurrentState(registerId);
        VisitTransitions.assertTransition(from, VisitEvent.FINISH);
        if (from != VisitState.IN_CONSULTATION) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅接诊中状态可结束看诊");
        }
        assertUpdated(registerRepository.markFinishedIfCurrent(registerId, from),
                registerId, VisitEvent.FINISH, "仅接诊中状态可结束看诊");
    }

    private int requireCurrentState(Long registerId) {
        return currentStateOrThrow(registerId);
    }

    private int currentStateOrThrow(Long registerId) {
        Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        return ((Number) register.get("visitState")).intValue();
    }

    private void assertUpdated(int updated, Long registerId, VisitEvent event, String mismatchHint) {
        if (updated > 0) {
            return;
        }
        int from = currentStateOrThrow(registerId);
        try {
            VisitTransitions.resolveTarget(from, event);
        } catch (VisitTransitionException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, mismatchHint);
        }
        throw concurrentChange(registerId, event);
    }

    private BusinessException concurrentChange(Long registerId, VisitEvent event) {
        return new BusinessException(ErrorCode.BAD_REQUEST,
                "挂号状态已变更，请刷新后重试（registerId=" + registerId + ", event=" + event + "）");
    }
}
