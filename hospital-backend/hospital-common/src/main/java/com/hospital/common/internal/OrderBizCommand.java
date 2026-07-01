package com.hospital.common.internal;

/**
 * 跨服务医嘱账单协同（ADR-019 · patient → clinical Feign）。
 */
public record OrderBizCommand(String bizType, Long bizId) {
}
