package com.hospital.common.constant;

/**
 * 处方状态：10 已开立 → 15 药师驳回 → 20 已缴费 → 30 已发药。
 */
public final class PrescriptionStatus {

    public static final int ORDERED = 10;
    public static final int PHARMACY_REJECTED = 15;
    public static final int PAID = 20;
    public static final int DISPENSED = 30;
    public static final int RETURNED = 40;
    public static final int REFUNDED = 50;

    private PrescriptionStatus() {
    }
}
