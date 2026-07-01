package com.hospital.patient.service;

import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PaymentChannel;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.dto.registrar.WindowChargeRequest;
import com.hospital.patient.repository.BillRepository;
import com.hospital.patient.repository.PaymentRepository;
import com.hospital.patient.security.AuthContext;
import com.hospital.patient.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrarChargeService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Transactional
    public Map<String, Object> windowCharge(WindowChargeRequest request) {
        Long operatorId = requireRegistrar();
        List<Long> billIds = request.getBillIds();
        if (billIds == null || billIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "billIds 不能为空");
        }

        String payChannel = normalizePayChannel(request.getPayChannel());
        billIds = billRepository.expandWithPendingMedicalBookBills(billIds);
        List<Map<String, Object>> bills = billRepository.findByIds(billIds);
        if (bills.size() != billIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账单不存在");
        }

        Set<Long> patientIds = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map<String, Object> bill : bills) {
            if (((Number) bill.get("status")).intValue() != BillStatus.PENDING) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在非待支付账单");
            }
            patientIds.add(((Number) bill.get("patientId")).longValue());
            totalAmount = totalAmount.add((BigDecimal) bill.get("amount"));
        }
        if (patientIds.size() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不可跨患者合并收费");
        }
        Long patientId = patientIds.iterator().next();

        long paymentId = paymentRepository.insertPayment(patientId, totalAmount, payChannel, operatorId, null);

        for (Map<String, Object> bill : bills) {
            long billId = ((Number) bill.get("id")).longValue();
            paymentRepository.linkBill(paymentId, billId, (BigDecimal) bill.get("amount"));
            billRepository.markPaid(billId);
            paymentService.settlePaidBill(bill);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", paymentId);
        result.put("paidAmount", totalAmount);
        result.put("payChannel", payChannel);
        result.put("channelLabel", com.hospital.common.constant.PaymentChannel.labelOf(payChannel));
        result.put("message", String.format("收费成功，实收 ¥%.2f", totalAmount));
        return result;
    }

    private String normalizePayChannel(String payChannel) {
        if (!StringUtils.hasText(payChannel)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "payChannel 不能为空");
        }
        String channel = payChannel.trim().toUpperCase(Locale.ROOT);
        if (!PaymentChannel.isRegistrarChargeAllowed(channel)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的支付方式: " + payChannel);
        }
        return channel;
    }

    private Long requireRegistrar() {
        AuthContext context = AuthContextHolder.require();
        if (!context.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要收费员身份");
        }
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
        return context.getEmployeeId();
    }
}
