package com.hospital.patient.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.internal.CreateBillCommand;
import com.hospital.common.internal.PrescriptionBillResubmitCommand;
import com.hospital.patient.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InternalBillService {

    private final BillRepository billRepository;

    @Transactional
    public Map<String, Object> createBill(CreateBillCommand command) {
        long billId = billRepository.insertBill(
                command.patientId(),
                command.registerId(),
                command.bizType(),
                command.bizId(),
                command.billTitle(),
                command.amount());
        Map<String, Object> result = new HashMap<>();
        result.put("billId", billId);
        return result;
    }

    @Transactional
    public Map<String, Object> resubmitPrescriptionBill(PrescriptionBillResubmitCommand command) {
        String billTitle = command.billTitle();
        long billId = billRepository.findByBiz(BillBizType.PRESCRIPTION, command.prescriptionId())
                .map(existing -> {
                    int status = ((Number) existing.get("status")).intValue();
                    if (status != BillStatus.REFUNDED) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "处方账单状态异常，无法重新提交");
                    }
                    Long existingBillId = ((Number) existing.get("id")).longValue();
                    if (billRepository.resetForResubmit(existingBillId, billTitle, command.amount()) == 0) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "处方账单重置失败");
                    }
                    return existingBillId;
                })
                .orElseGet(() -> billRepository.insertBill(
                        command.patientId(),
                        command.registerId(),
                        BillBizType.PRESCRIPTION,
                        command.prescriptionId(),
                        billTitle,
                        command.amount()));

        Map<String, Object> result = new HashMap<>();
        result.put("billId", billId);
        return result;
    }
}
