package com.hospital.common.internal;

/**
 * 药师驳回处方并退费（ADR-019 · pharmacy → patient Feign）。
 */
public record PrescriptionPharmacyRejectCommand(Long prescriptionId, String reason, Long pharmacistId) {
}
