package com.hospital.pharmacy.order;

import com.hospital.pharmacy.repository.DrugRepository;
import com.hospital.pharmacy.repository.PrescriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionInventorySupportTest {

    @Mock
    private DrugRepository drugRepository;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @InjectMocks
    private PrescriptionInventorySupport prescriptionInventorySupport;

    @Test
    void restorePrescription_restoresEachItemStock() {
        when(prescriptionRepository.findItemsByPrescriptionId(100L)).thenReturn(List.of(
                Map.of("drugId", 1L, "quantity", new BigDecimal("2.00")),
                Map.of("drugId", 2L, "quantity", new BigDecimal("1.50"))
        ));

        prescriptionInventorySupport.restorePrescription(100L);

        verify(drugRepository).restoreStock(1L, new BigDecimal("2.00"));
        verify(drugRepository).restoreStock(2L, new BigDecimal("1.50"));
    }
}
