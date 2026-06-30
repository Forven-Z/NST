package com.hospital.common.support;

import com.hospital.common.constant.VisitState;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 挂号生命周期：退号窗口、待支付超时、日终关单（方案 A：未叫号可退，21:00 关单）。
 */
public final class RegisterLifecycleSupport {

    public static final String REMARK_AUTO_DAY_CLOSE = "AUTO_DAY_CLOSE";

    public static final int PAYMENT_TIMEOUT_MINUTES = 10;

    public static final int DAY_CLOSE_HOUR = 21;

    private RegisterLifecycleSupport() {
    }

    /** 待支付：10 分钟内可主动取消；超时由定时任务关闭。 */
    public static boolean isPendingPaymentActive(int visitState, OffsetDateTime createTime, OffsetDateTime now) {
        if (visitState != VisitState.PENDING_PAYMENT) {
            return false;
        }
        if (createTime == null) {
            return true;
        }
        return !isPendingPaymentExpired(createTime, now);
    }

    public static boolean isPendingPaymentExpired(OffsetDateTime createTime, OffsetDateTime now) {
        if (createTime == null) {
            return false;
        }
        return ChronoUnit.MINUTES.between(createTime, now) >= PAYMENT_TIMEOUT_MINUTES;
    }

    /** 已缴费未叫号：可退号退费。 */
    public static boolean isPaidRegisterCancellable(int visitState, OffsetDateTime callTime) {
        return visitState == VisitState.REGISTERED && callTime == null;
    }

    public static boolean isCancellable(int visitState, OffsetDateTime callTime, OffsetDateTime createTime,
                                        OffsetDateTime now) {
        if (visitState == VisitState.PENDING_PAYMENT) {
            return isPendingPaymentActive(visitState, createTime, now);
        }
        return isPaidRegisterCancellable(visitState, callTime);
    }

    /** 是否到达日终关单时刻（visit_date 当天 21:00 后，或 visit_date 早于今天）。 */
    public static boolean isDayCloseDue(LocalDate visitDate, LocalDate today, LocalTime now) {
        if (visitDate == null) {
            return false;
        }
        if (visitDate.isBefore(today)) {
            return true;
        }
        if (visitDate.isAfter(today)) {
            return false;
        }
        return !now.isBefore(LocalTime.of(DAY_CLOSE_HOUR, 0));
    }

    public static String cancelHint(int visitState, OffsetDateTime callTime, OffsetDateTime createTime,
                                    OffsetDateTime now) {
        if (visitState == VisitState.PENDING_PAYMENT) {
            if (isPendingPaymentExpired(createTime, now)) {
                return "支付已超时，挂号已自动关闭";
            }
            return "请在占号后 " + PAYMENT_TIMEOUT_MINUTES + " 分钟内完成支付";
        }
        if (visitState == VisitState.REGISTERED && callTime == null) {
            return "医生叫号后将不可退号；当日 " + DAY_CLOSE_HOUR + ":00 未就诊将自动结束且不退挂号费";
        }
        if (visitState == VisitState.REGISTERED || visitState == VisitState.IN_CONSULTATION) {
            return "当日 " + DAY_CLOSE_HOUR + ":00 未结束看诊将自动关单";
        }
        return null;
    }
}
