package com.hospital.common.order;

/**
 * 处方（SM2）状态事件。
 */
public enum PrescriptionEvent {

    /** 医生开立 → 10 */
    ORDER,

    /** 驳回后重提 → 10 */
    RESUBMIT,

    /** 缴费 → 20 */
    PAY,

    /** 药师驳回 → 15 */
    PHARMACY_REJECT,

    /** 发药 → 30 */
    DISPENSE,

    /** 退药 → 40 */
    RETURN_DRUG,

    /** 退费 → 50 */
    REFUND
}
