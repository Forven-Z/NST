package com.hospital.common.internal;

import java.math.BigDecimal;

/**
 * clinical his 开单后由 patient 创建账单（ADR-019 · 方案 A）。
 */
public record CreateBillCommand(
        Long patientId,
        Long registerId,
        String bizType,
        Long bizId,
        String billTitle,
        BigDecimal amount
) {
}
