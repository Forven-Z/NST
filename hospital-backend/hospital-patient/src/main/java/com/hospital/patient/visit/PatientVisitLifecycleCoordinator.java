package com.hospital.patient.visit;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.visit.VisitEvent;
import com.hospital.common.visit.VisitTransitionException;
import com.hospital.common.visit.VisitTransitions;
import com.hospital.patient.repository.RegisterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 患者域 {@code register.visit_state} 写入口（ADR-019）：支付/取消/超时/退号/日终关单。
 */
@Service
@RequiredArgsConstructor
public class PatientVisitLifecycleCoordinator {

    private final RegisterRepository registerRepository;

    @Transactional
    public void payRegistration(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.PAY_REGISTRATION, "仅待支付挂号可完成支付");
    }

    @Transactional
    public void cancelPending(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.CANCEL_PENDING, "仅待支付挂号可取消");
    }

    @Transactional
    public void expirePending(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.EXPIRE_PENDING, "仅待支付挂号可超时关闭");
    }

    @Transactional
    public void cancelRegistered(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.CANCEL_REGISTERED, "仅已挂号未叫号可退号");
    }

    @Transactional
    public boolean autoDayClose(Long registerId, String remark) {
        int from = currentStateOrThrow(registerId);
        if (!VisitTransitions.canTransition(from, VisitEvent.AUTO_DAY_CLOSE)) {
            return false;
        }
        int updated = registerRepository.markAutoDayClosedIfCurrent(registerId, from, remark);
        if (updated == 0) {
            from = currentStateOrThrow(registerId);
            if (!VisitTransitions.canTransition(from, VisitEvent.AUTO_DAY_CLOSE)) {
                return false;
            }
            throw concurrentChange(registerId, VisitEvent.AUTO_DAY_CLOSE);
        }
        return true;
    }

    private void applySimpleTransition(Long registerId, VisitEvent event, String mismatchHint) {
        int from = currentStateOrThrow(registerId);
        int to = VisitTransitions.resolveTarget(from, event);
        int updated = registerRepository.updateVisitStateIfCurrent(registerId, from, to);
        if (updated == 0) {
            from = currentStateOrThrow(registerId);
            try {
                VisitTransitions.resolveTarget(from, event);
            } catch (VisitTransitionException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, mismatchHint);
            }
            throw concurrentChange(registerId, event);
        }
    }

    private int currentStateOrThrow(Long registerId) {
        Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        return ((Number) register.get("visitState")).intValue();
    }

    private BusinessException concurrentChange(Long registerId, VisitEvent event) {
        return new BusinessException(ErrorCode.BAD_REQUEST,
                "挂号状态已变更，请刷新后重试（registerId=" + registerId + ", event=" + event + "）");
    }
}
