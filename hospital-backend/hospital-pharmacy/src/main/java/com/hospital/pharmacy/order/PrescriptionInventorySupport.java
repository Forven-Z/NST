package com.hospital.pharmacy.order;

import com.hospital.pharmacy.repository.DrugRepository;
import com.hospital.pharmacy.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PrescriptionInventorySupport {

    private final DrugRepository drugRepository;
    private final PrescriptionRepository prescriptionRepository;

    public void restorePrescription(Long prescriptionId) {
        List<Map<String, Object>> items = prescriptionRepository.findItemsByPrescriptionId(prescriptionId);
        for (Map<String, Object> item : items) {
            Long drugId = ((Number) item.get("drugId")).longValue();
            BigDecimal quantity = (BigDecimal) item.get("quantity");
            drugRepository.restoreStock(drugId, quantity);
        }
    }
}
