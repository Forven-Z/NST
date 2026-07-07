package com.hospital.pharmacy.order.state;

import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.order.PrescriptionTransitionException;
import com.hospital.pharmacy.order.PrescriptionInventorySupport;
import com.hospital.pharmacy.repository.PrescriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusCoordinatorTest {

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private PrescriptionInventorySupport prescriptionInventorySupport;

    @InjectMocks
    private OrderStatusCoordinator orderStatusCoordinator;

    @Test
    void pharmacyReject_idempotentWhenAlreadyRejected() {
        when(prescriptionRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.PHARMACY_REJECTED)));

        assertThatCode(() -> orderStatusCoordinator.pharmacyReject(1L, 10L, "库存不足"))
                .doesNotThrowAnyException();

        verify(prescriptionInventorySupport, never()).restorePrescription(1L);
        verify(prescriptionRepository, never()).markPharmacyRejected(1L, 10L, "库存不足");
    }

    @Test
    void pharmacyReject_rejectsNonPaidStatus() {
        when(prescriptionRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.ORDERED)));

        assertThatThrownBy(() -> orderStatusCoordinator.pharmacyReject(2L, 10L, "库存不足"))
                .isInstanceOf(PrescriptionTransitionException.class);
    }

    @Test
    void pharmacyReject_success() {
        when(prescriptionRepository.findByIdForUpdate(3L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.PAID)));
        when(prescriptionRepository.markPharmacyRejected(3L, 10L, "库存不足")).thenReturn(1);

        orderStatusCoordinator.pharmacyReject(3L, 10L, "库存不足");

        verify(prescriptionInventorySupport).restorePrescription(3L);
        verify(prescriptionRepository).markPharmacyRejected(3L, 10L, "库存不足");
    }

    @Test
    void dispensePrescription_rejectsOrderedStatus() {
        when(prescriptionRepository.findByIdForUpdate(4L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.ORDERED)));

        assertThatThrownBy(() -> orderStatusCoordinator.dispensePrescription(4L, 10L))
                .isInstanceOf(PrescriptionTransitionException.class);
    }

    @Test
    void returnPrescriptionDrug_rejectsPaidStatus() {
        when(prescriptionRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(Map.of("status", PrescriptionStatus.PAID)));

        assertThatThrownBy(() -> orderStatusCoordinator.returnPrescriptionDrug(5L))
                .isInstanceOf(PrescriptionTransitionException.class);
    }
}
