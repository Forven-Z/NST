package com.hospital.patient.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.dto.patient.MockPaymentRequest;
import com.hospital.patient.repository.BillRepository;
import com.hospital.patient.repository.CheckRequestRepository;
import com.hospital.patient.repository.DisposalRequestRepository;
import com.hospital.patient.repository.InspectionRequestRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.client.ClinicalOrderBridge;
import com.hospital.patient.clinicalsync.ClinicalSyncService;
import com.hospital.patient.repository.PaymentRepository;
import com.hospital.patient.repository.PrescriptionRepository;
import com.hospital.patient.repository.RegisterRepository;
import com.hospital.patient.visit.PatientVisitLifecycleCoordinator;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String MOCK_CHANNEL = "WECHAT";

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final RegisterRepository registerRepository;
    private final PatientRepository patientRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final PatientFamilyService patientFamilyService;
    private final PatientVisitLifecycleCoordinator visitLifecycleCoordinator;
    private final ClinicalOrderBridge clinicalOrderBridge;
    private final ClinicalSyncService clinicalSyncService;

    @Transactional
    public Map<String, Object> mockPay(MockPaymentRequest request) {
        Long operatorId = AuthContextHolder.require().getPatientId();
        List<Long> billIds = billRepository.expandWithPendingMedicalBookBills(request.getBillIds());
        List<Map<String, Object>> bills = billRepository.findByIds(billIds);
        if (bills.isEmpty() || bills.size() != billIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账单不存在或无权支付");
        }
        for (Map<String, Object> bill : bills) {
            Long billPatientId = ((Number) bill.get("patientId")).longValue();
            if (!patientFamilyService.canAccessVisitPatient(operatorId, billPatientId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权支付该账单");
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> bill : bills) {
            if (((Number) bill.get("status")).intValue() != BillStatus.PENDING) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在非待支付账单");
            }
            total = total.add((BigDecimal) bill.get("amount"));
        }

        long paymentId = paymentRepository.insertPayment(operatorId, total, MOCK_CHANNEL);
        List<Long> clinicalSyncTaskIds = clinicalSyncService.newTaskIdList();

        for (Map<String, Object> bill : bills) {
            long billId = ((Number) bill.get("id")).longValue();
            paymentRepository.linkBill(paymentId, billId, (BigDecimal) bill.get("amount"));
            billRepository.markPaid(billId);
            settlePaidBillLocally(bill, clinicalSyncTaskIds);
        }

        clinicalSyncService.scheduleProcessAfterCommit(clinicalSyncTaskIds);

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", paymentId);
        result.put("status", 1);
        result.put("totalAmount", total);
        result.put("message", "模拟支付成功");
        return result;
    }

    public void settlePaidBill(Map<String, Object> bill) {
        List<Long> clinicalSyncTaskIds = clinicalSyncService.newTaskIdList();
        settlePaidBillLocally(bill, clinicalSyncTaskIds);
        clinicalSyncService.scheduleProcessAfterCommit(clinicalSyncTaskIds);
    }

    private void settlePaidBillLocally(Map<String, Object> bill, List<Long> clinicalSyncTaskIds) {
        String bizType = (String) bill.get("bizType");
        long bizId = ((Number) bill.get("bizId")).longValue();

        if (BillBizType.REGISTER.equals(bizType)) {
            visitLifecycleCoordinator.payRegistration(bizId);
            registerRepository.findById(bizId).ifPresent(reg -> {
                BigDecimal registFee = (BigDecimal) reg.get("registFee");
                BigDecimal billAmount = (BigDecimal) bill.get("amount");
                if (registFee != null && billAmount.compareTo(registFee) > 0) {
                    long patientId = ((Number) bill.get("patientId")).longValue();
                    patientRepository.updateNeedMedicalBook(patientId, true);
                }
            });
        } else if (BillBizType.MEDICAL_BOOK.equals(bizType)) {
            long patientId = ((Number) bill.get("patientId")).longValue();
            patientRepository.updateNeedMedicalBook(patientId, true);
        } else if (clinicalOrderBridge.handles(bizType)) {
            long taskId = clinicalSyncService.enqueueOnBillPaid(bizType, bizId);
            clinicalSyncTaskIds.add(taskId);
        }
    }

    public Map<String, Object> listPendingBills(Long visitPatientId) {
        return listBills(visitPatientId, BillStatus.PENDING, null, null);
    }

    public Map<String, Object> listBills(Long visitPatientId, Integer status, Long registerId, String scope) {
        Long visitId = patientFamilyService.resolveVisitPatientId(visitPatientId);
        List<Map<String, Object>> list = billRepository.findByPatientIdForDisplay(visitId, status);
        if (registerId != null) {
            list = list.stream()
                    .filter(row -> registerId.equals(row.get("registerId")))
                    .toList();
        }
        if (StringUtils.hasText(scope)) {
            list = list.stream()
                    .filter(row -> FinancialQueryService.matchesBillScope((String) row.get("bizType"), scope))
                    .toList();
        }
        List<Map<String, Object>> enriched = list.stream().map(this::enrichBillRow).toList();
        return Map.of("list", enriched);
    }

    private Map<String, Object> enrichBillRow(Map<String, Object> bill) {
        Map<String, Object> row = new HashMap<>(bill);
        String bizType = (String) bill.get("bizType");
        row.put("bizTypeLabel", bizTypeLabel(bizType));
        row.put("lineItems", buildLineItems(bill));
        return row;
    }

    private List<Map<String, Object>> buildLineItems(Map<String, Object> bill) {
        String bizType = (String) bill.get("bizType");
        long bizId = ((Number) bill.get("bizId")).longValue();
        List<Map<String, Object>> items = new ArrayList<>();

        if (BillBizType.PRESCRIPTION.equals(bizType)) {
            for (Map<String, Object> drug : prescriptionRepository.findItemsByPrescriptionId(bizId)) {
                Map<String, Object> line = new HashMap<>();
                line.put("name", drug.get("drugName"));
                line.put("spec", drug.get("drugFormat"));
                line.put("qty", drug.get("quantity"));
                line.put("amount", drug.get("amount"));
                line.put("usage", formatDrugUsage(drug));
                items.add(line);
            }
            return items;
        }

        if (BillBizType.REGISTER.equals(bizType) || "REGIST".equals(bizType)) {
            items.add(singleLine(bill.get("billTitle"), "门诊挂号", bill.get("amount")));
            return items;
        }

        if (BillBizType.INSPECTION.equals(bizType)) {
            inspectionRequestRepository.findById(bizId).ifPresent(req ->
                    items.add(singleLine(req.get("itemName"), req.get("purpose"), bill.get("amount"))));
        } else if (BillBizType.CHECK.equals(bizType)) {
            checkRequestRepository.findById(bizId).ifPresent(req ->
                    items.add(singleLine(req.get("itemName"), req.get("bodyPart"), bill.get("amount"))));
        } else if (BillBizType.DISPOSAL.equals(bizType)) {
            disposalRequestRepository.findById(bizId).ifPresent(req ->
                    items.add(singleLine(req.get("itemName"), req.get("purpose"), bill.get("amount"))));
        }

        if (items.isEmpty()) {
            items.add(singleLine(bill.get("billTitle"), null, bill.get("amount")));
        }
        return items;
    }

    private Map<String, Object> singleLine(Object name, Object sub, Object amount) {
        Map<String, Object> line = new HashMap<>();
        line.put("name", name);
        line.put("spec", sub);
        line.put("amount", amount);
        return line;
    }

    private String formatDrugUsage(Map<String, Object> drug) {
        List<String> parts = new ArrayList<>();
        if (drug.get("usageMethod") != null) {
            parts.add(String.valueOf(drug.get("usageMethod")));
        }
        if (drug.get("dosage") != null) {
            parts.add(String.valueOf(drug.get("dosage")));
        }
        if (drug.get("frequency") != null) {
            parts.add(String.valueOf(drug.get("frequency")));
        }
        if (drug.get("days") != null) {
            parts.add(drug.get("days") + "天");
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static String bizTypeLabel(String bizType) {
        if (bizType == null) {
            return "—";
        }
        return switch (bizType) {
            case BillBizType.REGISTER, "REGIST" -> "挂号";
            case BillBizType.INSPECTION -> "检验";
            case BillBizType.CHECK -> "检查";
            case BillBizType.PRESCRIPTION -> "处方";
            case BillBizType.DISPOSAL -> "处置";
            case BillBizType.MEDICAL_BOOK -> "病历本";
            default -> bizType;
        };
    }
}
