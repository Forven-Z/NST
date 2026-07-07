package com.hospital.pharmacy.service;

import com.hospital.common.Result;
import com.hospital.common.auth.UserType;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.pharmacy.client.PatientRefundFeignClient;
import com.hospital.pharmacy.dto.pharmacy.RejectPrescriptionRequest;
import com.hospital.pharmacy.order.state.OrderStatusCoordinator;
import com.hospital.pharmacy.repository.PrescriptionRepository;
import com.hospital.pharmacy.security.AuthContext;
import com.hospital.pharmacy.security.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacyServiceTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private PatientRefundFeignClient patientRefundFeignClient;

    @Mock
    private OrderStatusCoordinator orderStatusCoordinator;

    @InjectMocks
    private PharmacyService pharmacyService;

    @BeforeEach
    void setPharmacistContext() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(1L)
                .employeeId(10L)
                .roles(List.of("PHARMACIST"))
                .build());
    }

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void rejectDispense_rejectsBlankReason() {
        RejectPrescriptionRequest request = new RejectPrescriptionRequest();
        request.setReason("   ");

        assertThatThrownBy(() -> pharmacyService.rejectDispense(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拒绝原因");

        verify(prescriptionRepository, never()).findByIdForUpdate(1L);
    }

    @Test
    void rejectDispense_idempotentWhenAlreadyRejected() {
        RejectPrescriptionRequest request = new RejectPrescriptionRequest();
        request.setReason("库存不足");
        when(prescriptionRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.PHARMACY_REJECTED)));

        Map<String, Object> result = pharmacyService.rejectDispense(2L, request);

        assertThat(result.get("idempotent")).isEqualTo(true);
        verify(patientRefundFeignClient, never()).prescriptionPharmacyReject(any());
    }

    @Test
    void rejectDispense_rejectsNonPaidStatus() {
        RejectPrescriptionRequest request = new RejectPrescriptionRequest();
        request.setReason("库存不足");
        when(prescriptionRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.ORDERED)));

        assertThatThrownBy(() -> pharmacyService.rejectDispense(3L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已缴费未发药处方可拒绝");
    }

    @Test
    void rejectDispense_success() {
        RejectPrescriptionRequest request = new RejectPrescriptionRequest();
        request.setReason("库存不足");
        when(prescriptionRepository.findByIdForUpdate(4L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.PAID)));
        when(patientRefundFeignClient.prescriptionPharmacyReject(any()))
                .thenReturn(Result.success(Map.of()));

        Map<String, Object> result = pharmacyService.rejectDispense(4L, request);

        assertThat(result.get("status")).isEqualTo(PrescriptionStatus.PHARMACY_REJECTED);
        verify(orderStatusCoordinator).pharmacyReject(4L, 10L, "库存不足");
    }

    @Test
    void dispense_requiresPharmacistRole() {
        AuthContextHolder.clear();
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(2L)
                .employeeId(20L)
                .roles(List.of("DOCTOR"))
                .build());

        assertThatThrownBy(() -> pharmacyService.dispense(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PHARMACIST");
    }
}
