package com.hospital.patient.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.internal.CreateBillCommand;
import com.hospital.common.internal.PrescriptionBillResubmitCommand;
import com.hospital.patient.repository.BillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalBillServiceTest {

    @Mock
    private BillRepository billRepository;

    @InjectMocks
    private InternalBillService internalBillService;

    @Test
    void createBillDelegatesToRepository() {
        CreateBillCommand command = new CreateBillCommand(
                1L, 2L, BillBizType.INSPECTION, 10L, "检验费-血常规", new BigDecimal("50.00"));
        when(billRepository.insertBill(1L, 2L, BillBizType.INSPECTION, 10L, "检验费-血常规", new BigDecimal("50.00")))
                .thenReturn(99L);

        Map<String, Object> result = internalBillService.createBill(command);

        assertThat(result.get("billId")).isEqualTo(99L);
    }

    @Test
    void resubmitPrescriptionBillResetsRefundedBill() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("id", 88L);
        existing.put("status", BillStatus.REFUNDED);
        when(billRepository.findByBiz(BillBizType.PRESCRIPTION, 300L)).thenReturn(Optional.of(existing));
        when(billRepository.resetForResubmit(88L, "处方费 #300", new BigDecimal("120.00"))).thenReturn(1);

        Map<String, Object> result = internalBillService.resubmitPrescriptionBill(
                new PrescriptionBillResubmitCommand(300L, 1L, 2L, "处方费 #300", new BigDecimal("120.00")));

        assertThat(result.get("billId")).isEqualTo(88L);
        verify(billRepository).resetForResubmit(88L, "处方费 #300", new BigDecimal("120.00"));
    }

    @Test
    void resubmitPrescriptionBillInsertsWhenBillMissing() {
        when(billRepository.findByBiz(BillBizType.PRESCRIPTION, 300L)).thenReturn(Optional.empty());
        when(billRepository.insertBill(
                eq(1L), eq(2L), eq(BillBizType.PRESCRIPTION), eq(300L), eq("处方费 #300"), any()))
                .thenReturn(77L);

        Map<String, Object> result = internalBillService.resubmitPrescriptionBill(
                new PrescriptionBillResubmitCommand(300L, 1L, 2L, "处方费 #300", new BigDecimal("120.00")));

        assertThat(result.get("billId")).isEqualTo(77L);
    }

    @Test
    void resubmitPrescriptionBillRejectsNonRefundedBill() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("id", 88L);
        existing.put("status", BillStatus.PAID);
        when(billRepository.findByBiz(BillBizType.PRESCRIPTION, 300L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> internalBillService.resubmitPrescriptionBill(
                new PrescriptionBillResubmitCommand(300L, 1L, 2L, "处方费 #300", new BigDecimal("120.00"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
