package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.doctor.CreatePrescriptionRequest;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.DrugRepository;
import com.hospital.his.repository.PrescriptionRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import com.hospital.his.util.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final RegisterRepository registerRepository;
    private final DrugRepository drugRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final BillRepository billRepository;

    @Transactional
    public Map<String, Object> createPrescription(CreatePrescriptionRequest request) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        if (doctorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }

        Map<String, Object> register = registerRepository.findById(request.getRegisterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        if (!doctorId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为本队列患者开立处方");
        }
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.IN_CONSULTATION && visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "患者尚未进入可开单状态");
        }

        Long patientId = ((Number) register.get("patientId")).longValue();
        List<Map<String, Object>> itemSnapshots = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortNo = 0;

        for (CreatePrescriptionRequest.PrescriptionItemRequest item : request.getItems()) {
            Map<String, Object> drug = drugRepository.findById(item.getDrugId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + item.getDrugId()));
            BigDecimal unitPrice = (BigDecimal) drug.get("retailPrice");
            BigDecimal amount = unitPrice.multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(amount);

            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("drug", drug);
            snapshot.put("item", item);
            snapshot.put("amount", amount);
            snapshot.put("sortNo", sortNo++);
            itemSnapshots.add(snapshot);
        }

        String prescriptionNo = BizNoGenerator.prescriptionNo();
        long prescriptionId = prescriptionRepository.insertPrescription(
                request.getRegisterId(), patientId, doctorId, prescriptionNo,
                totalAmount, PrescriptionStatus.ORDERED, request.getRemark());

        for (Map<String, Object> snapshot : itemSnapshots) {
            @SuppressWarnings("unchecked")
            Map<String, Object> drug = (Map<String, Object>) snapshot.get("drug");
            CreatePrescriptionRequest.PrescriptionItemRequest item =
                    (CreatePrescriptionRequest.PrescriptionItemRequest) snapshot.get("item");
            prescriptionRepository.insertItem(
                    prescriptionId,
                    item.getDrugId(),
                    (String) drug.get("drugCode"),
                    (String) drug.get("drugName"),
                    (String) drug.get("specification"),
                    (BigDecimal) drug.get("retailPrice"),
                    item.getQuantity(),
                    (BigDecimal) snapshot.get("amount"),
                    item.getUsageMethod(),
                    item.getDosage(),
                    item.getFrequency(),
                    item.getDays(),
                    item.getEntrust(),
                    (Integer) snapshot.get("sortNo"));
        }

        String billNo = BizNoGenerator.billNo();
        long billId = billRepository.insertBill(
                billNo, patientId, request.getRegisterId(), BillBizType.PRESCRIPTION, prescriptionId,
                "处方费-" + prescriptionNo, totalAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionId", prescriptionId);
        result.put("prescriptionNo", prescriptionNo);
        result.put("totalAmount", totalAmount);
        result.put("status", PrescriptionStatus.ORDERED);
        result.put("billId", billId);
        result.put("billNo", billNo);
        return result;
    }
}
