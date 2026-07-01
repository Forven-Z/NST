package com.hospital.common.internal;

import java.math.BigDecimal;

public record PrescriptionBillResubmitCommand(
        Long prescriptionId,
        Long patientId,
        Long registerId,
        String billTitle,
        BigDecimal amount
) {
}
