package com.hospital.common.constant;

/**
 * 挂号看诊状态 — 与 DATABASE_DESIGN §1.5 一致；0 为 P1 待支付中间态。
 */
public final class VisitState {

    public static final int PENDING_PAYMENT = 0;
    public static final int REGISTERED = 1;
    public static final int IN_CONSULTATION = 2;
    public static final int FINISHED = 3;
    public static final int CANCELLED = 4;

    private VisitState() {
    }
}
