package com.hospital.patient.service;

import com.hospital.common.auth.UserType;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.repository.RegisterRepository;
import com.hospital.patient.security.AuthContext;
import com.hospital.patient.security.AuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterCancelServiceTest {

    @Mock
    private RegisterRepository registerRepository;

    @Mock
    private RefundService refundService;

    @Mock
    private RegisterLifecycleService registerLifecycleService;

    @InjectMocks
    private RegisterCancelService registerCancelService;

    @AfterEach
    void clearContext() {
        AuthContextHolder.clear();
    }

    @Test
    void cancelByPatient_requiresPatientIdentity() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(1L)
                .employeeId(10L)
                .roles(List.of("REGISTRAR"))
                .build());

        assertThatThrownBy(() -> registerCancelService.cancelByPatient(100L, "临时有事"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需要患者身份");
    }

    @Test
    void cancelByPatient_registerNotFound() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.PATIENT)
                .userId(1L)
                .patientId(200L)
                .build());
        when(registerRepository.findDetailForOwner(100L, 200L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registerCancelService.cancelByPatient(100L, "临时有事"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("挂号记录不存在");
    }

    @Test
    void cancelByRegistrar_requiresRegistrarRole() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(1L)
                .employeeId(10L)
                .roles(List.of("DOCTOR"))
                .build());

        assertThatThrownBy(() -> registerCancelService.cancelByRegistrar(100L, "窗口退号"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REGISTRAR");
    }

    @Test
    void cancelByRegistrar_pendingPaymentDelegatesToLifecycle() {
        AuthContextHolder.set(AuthContext.builder()
                .userType(UserType.STAFF)
                .userId(1L)
                .employeeId(10L)
                .roles(List.of("REGISTRAR"))
                .build());
        Map<String, Object> register = new HashMap<>();
        register.put("visitState", VisitState.PENDING_PAYMENT);
        when(registerRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(register));
        when(registerLifecycleService.cancelPendingRegister(100L, "窗口退号"))
                .thenReturn(Map.of("registerId", 100L));

        registerCancelService.cancelByRegistrar(100L, "窗口退号");

        verify(registerLifecycleService).assertCancellableForCancel(register);
        verify(registerLifecycleService).cancelPendingRegister(100L, "窗口退号");
        verify(refundService, never()).refundRegisterBill(100L, "窗口退号", 10L);
    }
}
