package com.hospital.his.client;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.internal.CreateBillCommand;
import com.hospital.common.internal.PrescriptionBillResubmitCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PatientBillBridge {

    private final PatientBillFeignClient patientBillFeignClient;

    public long createBill(Long patientId, Long registerId, String bizType, Long bizId,
                           String billTitle, BigDecimal amount) {
        Result<Map<String, Object>> result = patientBillFeignClient.createBill(
                new CreateBillCommand(patientId, registerId, bizType, bizId, billTitle, amount));
        return extractBillId(result, "创建账单失败");
    }

    public long resubmitPrescriptionBill(Long prescriptionId, Long patientId, Long registerId,
                                         String billTitle, BigDecimal amount) {
        Result<Map<String, Object>> result = patientBillFeignClient.resubmitPrescriptionBill(
                new PrescriptionBillResubmitCommand(prescriptionId, patientId, registerId, billTitle, amount));
        return extractBillId(result, "处方账单重新提交失败");
    }

    private long extractBillId(Result<Map<String, Object>> result, String fallbackMessage) {
        if (result == null || !Boolean.TRUE.equals(result.getSuccess()) || result.getData() == null) {
            String message = result != null ? result.getMessage() : fallbackMessage;
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        Object billId = result.getData().get("billId");
        if (billId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fallbackMessage);
        }
        return ((Number) billId).longValue();
    }
}
