package com.hospital.patient.service;

import com.hospital.common.auth.UserType;
import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.client.ClinicalOrderBridge;
import com.hospital.patient.clinicalsync.ClinicalSyncService;
import com.hospital.patient.repository.BillRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.repository.PaymentRepository;
import com.hospital.patient.repository.RefundRepository;
import com.hospital.patient.repository.RegisterRepository;
import com.hospital.patient.repository.SchedulingRepository;
import com.hospital.patient.security.AuthContext;
import com.hospital.patient.security.AuthContextHolder;
import com.hospital.patient.visit.PatientVisitLifecycleCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private RegisterRepository registerRepository;
    @Mock
    private SchedulingRepository schedulingRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientFamilyService patientFamilyService;
    @Mock
    private RegisterLifecycleService registerLifecycleService;
    @Mock
    private PatientVisitLifecycleCoordinator visitLifecycleCoordinator;
    @Mock
    private ClinicalOrderBridge clinicalOrderBridge;
    @Mock
    private ClinicalSyncService clinicalSyncService;

    @InjectMocks
    private RefundService refundService;

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void refundByPatient_requiresPatientIdentity() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(1L)
                .employeeId(10L)
                .build());

        assertThatThrownBy(() -> refundService.refundByPatient(1L, "退号"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需要患者身份");
    }

    @Test
    void refundRegisterBill_rejectsWhenPaidBillMissing() {
        when(billRepository.findPaidByBiz(BillBizType.REGISTER, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.refundRegisterBill(100L, "退号", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到已支付的挂号账单");
    }

    @Test
    void refundByPatient_rejectsNonPaidBill() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.PATIENT)
                .userId(1L)
                .patientId(200L)
                .build());
        Map<String, Object> bill = new HashMap<>();
        bill.put("id", 1L);
        bill.put("patientId", 200L);
        bill.put("status", BillStatus.REFUNDED);
        bill.put("bizType", BillBizType.REGISTER);
        bill.put("bizId", 100L);
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(patientFamilyService.canAccessVisitPatient(200L, 200L)).thenReturn(true);

        assertThatThrownBy(() -> refundService.refundByPatient(1L, "退号"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已支付账单可退费");
    }

    @Test
    void refundPrescriptionBillForPharmacyReject_idempotentWhenAlreadyRefunded() {
        Map<String, Object> bill = new HashMap<>();
        bill.put("id", 88L);
        bill.put("status", BillStatus.REFUNDED);
        bill.put("amount", 120);
        when(billRepository.findPaidByBiz(BillBizType.PRESCRIPTION, 300L)).thenReturn(Optional.empty());
        when(billRepository.findByBiz(BillBizType.PRESCRIPTION, 300L)).thenReturn(Optional.of(bill));

        Map<String, Object> result = refundService.refundPrescriptionBillForPharmacyReject(
                300L, "库存不足", 10L);

        assertThat(result.get("idempotent")).isEqualTo(true);
        assertThat(result.get("prescriptionId")).isEqualTo(300L);
    }

    @Test
    void refundByRegistrar_requiresRegistrarRole() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(1L)
                .employeeId(10L)
                .build());

        assertThatThrownBy(() -> refundService.refundByRegistrar(1L, "窗口退费"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REGISTRAR");
    }
}
