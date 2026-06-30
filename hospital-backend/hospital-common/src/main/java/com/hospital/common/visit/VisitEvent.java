package com.hospital.common.visit;

/**
 * 就诊生命周期事件 — 与 {@link VisitTransitions}、{@code register.visit_state} 对应。
 */
public enum VisitEvent {

    /** 患者/窗口支付挂号费：0 → 1 */
    PAY_REGISTRATION,

    /** 用户主动取消待支付占号：0 → 4 */
    CANCEL_PENDING,

    /** 待支付超时自动关闭：0 → 4 */
    EXPIRE_PENDING,

    /** 医生叫号：1 → 2 */
    CALL,

    /** 已挂号未叫号退号（含退挂号费）：1 → 4 */
    CANCEL_REGISTERED,

    /** 医生结束看诊（病历已提交）：2 → 3 */
    FINISH,

    /** 当日 21:00 自动关单：1/2 → 3 */
    AUTO_DAY_CLOSE
}
