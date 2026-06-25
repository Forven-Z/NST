package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.RegisterLifecycleSupport;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.repository.SchedulingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterLifecycleService {

    private final RegisterRepository registerRepository;
    private final BillRepository billRepository;
    private final SchedulingRepository schedulingRepository;

    public Map<String, Object> enrichRegisterRow(Map<String, Object> row) {
        Map<String, Object> enriched = new HashMap<>(row);
        int visitState = ((Number) row.get("visitState")).intValue();
        OffsetDateTime callTime = (OffsetDateTime) row.get("callTime");
        OffsetDateTime createTime = (OffsetDateTime) row.get("createTime");
        if (createTime == null) {
            createTime = (OffsetDateTime) row.get("registTime");
        }
        OffsetDateTime now = OffsetDateTime.now();
        enriched.put("cancellable", RegisterLifecycleSupport.isCancellable(
                visitState, callTime, createTime, now));
        enriched.put("cancelHint", RegisterLifecycleSupport.cancelHint(
                visitState, callTime, createTime, now));
        return enriched;
    }

    public void assertCancellableForCancel(Map<String, Object> register) {
        int visitState = ((Number) register.get("visitState")).intValue();
        OffsetDateTime callTime = (OffsetDateTime) register.get("callTime");
        OffsetDateTime createTime = (OffsetDateTime) register.get("createTime");
        OffsetDateTime now = OffsetDateTime.now();

        if (visitState == VisitState.PENDING_PAYMENT) {
            if (RegisterLifecycleSupport.isPendingPaymentExpired(createTime, now)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "支付已超时，挂号已关闭");
            }
            return;
        }
        if (visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号未叫号状态可退号");
        }
        if (callTime != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "医生已叫号，不可退号");
        }
    }

    public void assertRegisterBillRefundable(Map<String, Object> register) {
        int visitState = ((Number) register.get("visitState")).intValue();
        OffsetDateTime callTime = (OffsetDateTime) register.get("callTime");
        if (!RegisterLifecycleSupport.isPaidRegisterCancellable(visitState, callTime)) {
            if (visitState == VisitState.IN_CONSULTATION || callTime != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "医生已叫号，不可退挂号费");
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号未叫号状态可退挂号费，请使用退号");
        }
    }

    @Transactional
    public Map<String, Object> cancelPendingRegister(Long registerId, String reason) {
        Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待支付挂号可取消");
        }

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

    @Transactional
    public int expirePendingPayments() {
        OffsetDateTime cutoff = OffsetDateTime.now()
                .minus(RegisterLifecycleSupport.PAYMENT_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        List<Long> ids = registerRepository.findIdsPendingPaymentExpired(cutoff);
        int closed = 0;
        for (Long id : ids) {
            try {
                cancelPendingRegister(id, "支付超时自动关闭");
                closed++;
            } catch (Exception ex) {
                log.warn("expire pending register failed id={}: {}", id, ex.getMessage());
            }
        }
        if (closed > 0) {
            log.info("Register payment timeout: closed {} pending registers", closed);
        }
        return closed;
    }

    @Transactional
    public int autoDayCloseRegisters() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalTime dayCloseTime = LocalTime.of(RegisterLifecycleSupport.DAY_CLOSE_HOUR, 0);
        List<Long> ids = registerRepository.findIdsDueForDayClose(today, dayCloseTime);
        int closed = 0;
        for (Long id : ids) {
            try {
                autoDayCloseOne(id);
                closed++;
            } catch (Exception ex) {
                log.warn("auto day close register failed id={}: {}", id, ex.getMessage());
            }
        }
        if (closed > 0) {
            log.info("Register day close: closed {} registers", closed);
        }
        return closed;
    }

    @Transactional
    public void autoDayCloseOne(Long registerId) {
        Map<String, Object> register = registerRepository.findByIdForUpdate(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.REGISTERED && visitState != VisitState.IN_CONSULTATION) {
            return;
        }
        registerRepository.markAutoDayClosed(registerId, RegisterLifecycleSupport.REMARK_AUTO_DAY_CLOSE);
    }

    @Transactional
    public void runMaintenanceOnStartup() {
        expirePendingPayments();
        autoDayCloseRegisters();
    }
}
