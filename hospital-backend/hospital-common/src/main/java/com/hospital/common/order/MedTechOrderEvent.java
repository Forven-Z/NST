package com.hospital.common.order;

/**
 * 医技医嘱（检验/检查/处置）状态事件 — 共用 SM1。
 */
public enum MedTechOrderEvent {

    /** 医生开立 → 10 */
    ORDER,

    /** 缴费 → 20 */
    PAY,

    /** 医技科开始执行 → 30 */
    EXECUTE,

    /** 录入结果 → 40 */
    RESULT_READY,

    /** 退费（未执行前）→ 50 */
    REFUND
}
