package com.hospital.his.order;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.DrugRepository;
import com.hospital.his.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 处方库存预扣/回增（与 SM2 事件同事务，由 {@link OrderStatusCoordinator} 调用）。
 */
@Component
@RequiredArgsConstructor
public class PrescriptionInventorySupport {

    private final DrugRepository drugRepository;
    private final PrescriptionRepository prescriptionRepository;

    public void validateAndDeduct(List<Map<String, Object>> itemSnapshots) {
        for (Map<String, Object> snapshot : itemSnapshots) {
            deductOne(snapshot);
        }
    }

    public void validateAndDeductPrescription(Long prescriptionId) {
        List<Map<String, Object>> items = prescriptionRepository.findItemsByPrescriptionId(prescriptionId);
        for (Map<String, Object> item : items) {
            deductLineItem(item);
        }
    }

    public void restorePrescription(Long prescriptionId) {
        List<Map<String, Object>> items = prescriptionRepository.findItemsByPrescriptionId(prescriptionId);
        for (Map<String, Object> item : items) {
            Long drugId = ((Number) item.get("drugId")).longValue();
            BigDecimal quantity = (BigDecimal) item.get("quantity");
            drugRepository.restoreStock(drugId, quantity);
        }
    }

    private void deductOne(Map<String, Object> snapshot) {
        @SuppressWarnings("unchecked")
        Map<String, Object> drug = (Map<String, Object>) snapshot.get("drug");
        var item = snapshot.get("item");
        BigDecimal quantity;
        Long drugId;
        String drugName;
        if (item instanceof com.hospital.his.dto.doctor.CreatePrescriptionRequest.PrescriptionItemRequest req) {
            quantity = req.getQuantity();
            drugId = req.getDrugId();
            drugName = (String) drug.get("drugName");
            drug = drugRepository.findByIdForUpdate(drugId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在"));
        } else {
            throw new IllegalArgumentException("unsupported item snapshot");
        }
        deductDrugLine(drugId, quantity, drugName, drug);
    }

    private void deductLineItem(Map<String, Object> item) {
        Long drugId = ((Number) item.get("drugId")).longValue();
        BigDecimal quantity = (BigDecimal) item.get("quantity");
        String drugName = (String) item.get("drugName");
        Map<String, Object> drug = drugRepository.findByIdForUpdate(drugId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在"));
        deductDrugLine(drugId, quantity, drugName, drug);
    }

    private void deductDrugLine(Long drugId, BigDecimal quantity, String drugName, Map<String, Object> drug) {
        int stock = drug.get("stockQty") != null ? ((Number) drug.get("stockQty")).intValue() : 0;
        if (quantity.compareTo(BigDecimal.valueOf(stock)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "该药品库存不足: " + drugName + "，当前库存 " + stock);
        }
        drugRepository.deductStock(drugId, quantity);
    }
}
