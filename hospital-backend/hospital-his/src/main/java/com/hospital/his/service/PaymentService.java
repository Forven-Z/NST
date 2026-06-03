package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.patient.MockPaymentRequest;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.CheckRequestRepository;
import com.hospital.his.repository.InspectionRequestRepository;
import com.hospital.his.repository.PrescriptionRepository;
import com.hospital.his.repository.PaymentRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import com.hospital.his.util.BizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final PrescriptionRepository prescriptionRepository;

    @Transactional
    public Map<String, Object> mockPay(MockPaymentRequest request) {
        Long patientId = AuthContextHolder.require().getPatientId();
        List<Map<String, Object>> bills = billRepository.findByIdsForPatient(request.getBillIds(), patientId);
        if (bills.isEmpty() || bills.size() != request.getBillIds().size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账单不存在或无权支付");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> bill : bills) {
            if (((Number) bill.get("status")).intValue() != BillStatus.PENDING) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在非待支付账单");
            }
            total = total.add((BigDecimal) bill.get("amount"));
        }

        String paymentNo = BizNoGenerator.paymentNo();
        long paymentId = paymentRepository.insertPayment(paymentNo, patientId, total, MOCK_CHANNEL);

        for (Map<String, Object> bill : bills) {
            long billId = ((Number) bill.get("id")).longValue();
            paymentRepository.linkBill(paymentId, billId, (BigDecimal) bill.get("amount"));
            billRepository.markPaid(billId);

            if (BillBizType.REGISTER.equals(bill.get("bizType"))) {
                long registerId = ((Number) bill.get("bizId")).longValue();
                registerRepository.updateVisitState(registerId, VisitState.REGISTERED);
            } else if (BillBizType.INSPECTION.equals(bill.get("bizType"))) {
                long inspectionId = ((Number) bill.get("bizId")).longValue();
                inspectionRequestRepository.updateStatus(inspectionId, InspectionRequestStatus.PAID);
            } else if (BillBizType.CHECK.equals(bill.get("bizType"))) {
                checkRequestRepository.updateStatus(((Number) bill.get("bizId")).longValue(), InspectionRequestStatus.PAID);
            } else if (BillBizType.PRESCRIPTION.equals(bill.get("bizType"))) {
                prescriptionRepository.updateStatus(((Number) bill.get("bizId")).longValue(), PrescriptionStatus.PAID);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", paymentId);
        result.put("paymentNo", paymentNo);
        result.put("status", 1);
        result.put("totalAmount", total);
        result.put("message", "模拟支付成功");
        return result;
    }

    public Map<String, Object> listPendingBills() {
        Long patientId = AuthContextHolder.require().getPatientId();
        return Map.of("list", billRepository.findPendingByPatient(patientId));
    }
}
