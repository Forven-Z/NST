package com.hospital.his.client;

import com.hospital.common.Result;
import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.internal.CreateBillCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientBillBridgeTest {

    @Mock
    private PatientBillFeignClient patientBillFeignClient;

    @InjectMocks
    private PatientBillBridge patientBillBridge;

    @Test
    void createBillReturnsBillIdFromFeign() {
        when(patientBillFeignClient.createBill(any(CreateBillCommand.class)))
                .thenReturn(Result.success(Map.of("billId", 42L)));

        long billId = patientBillBridge.createBill(
                1L, 2L, BillBizType.CHECK, 10L, "检查费-CT", new BigDecimal("300.00"));

        assertThat(billId).isEqualTo(42L);
    }

    @Test
    void createBillPropagatesFeignFailure() {
        when(patientBillFeignClient.createBill(any(CreateBillCommand.class)))
                .thenReturn(Result.fail(ErrorCode.BAD_REQUEST, "patient 不可用"));

        assertThatThrownBy(() -> patientBillBridge.createBill(
                1L, 2L, BillBizType.CHECK, 10L, "检查费-CT", new BigDecimal("300.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("patient 不可用");
    }
}
