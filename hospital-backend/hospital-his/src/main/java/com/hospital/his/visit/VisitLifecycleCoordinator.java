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
 * {@code register.visit_state} 的<strong>唯一写入口</strong>（步骤 ① ADR-018）。
 * <p>
 * 所有状态变更须先经 {@link VisitTransitions} 校验，再落库；业务侧守卫（退号条件、病历提交等）仍由各 Service 负责。
 */
@Service
@RequiredArgsConstructor
public class VisitLifecycleCoordinator {

    private final RegisterRepository registerRepository;

    /** 支付挂号费：0 → 1 */
    @Transactional
    public void payRegistration(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.PAY_REGISTRATION, "仅待支付挂号可完成支付");
    }

    /** 用户/窗口取消待支付占号：0 → 4 */
    @Transactional
    public void cancelPending(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.CANCEL_PENDING, "仅待支付挂号可取消");
    }

    /** 待支付超时：0 → 4 */
    @Transactional
    public void expirePending(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.EXPIRE_PENDING, "仅待支付挂号可超时关闭");
    }

    /** 已挂号未叫号退号：1 → 4 */
    @Transactional
    public void cancelRegistered(Long registerId) {
        applySimpleTransition(registerId, VisitEvent.CANCEL_REGISTERED, "仅已挂号未叫号可退号");
    }

    /** 医生叫号：1 → 2 */
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

    /** 结束看诊：2 → 3 */
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

    /**
     * 当日 21:00 自动关单：1/2 → 3。
     *
     * @return {@code true} 已关单；{@code false} 当前状态无需关单（已结束/已退号等）
     */
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
        int from = requireCurrentState(registerId);
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

    private int requireCurrentState(Long registerId) {
        int from = currentStateOrThrow(registerId);
        return from;
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
