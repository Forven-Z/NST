package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.CheckRequestRepository;
import com.hospital.his.repository.DisposalRequestRepository;
import com.hospital.his.repository.InspectionRequestRepository;
import com.hospital.his.repository.PrescriptionRepository;
import com.hospital.his.repository.RefundRepository;
import com.hospital.his.repository.PaymentRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.repository.SchedulingRepository;
import com.hospital.his.security.AuthContext;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RegisterRepository registerRepository;
    private final InspectionRequestRepository inspectionRequestRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final SchedulingRepository schedulingRepository;
    private final PatientFamilyService patientFamilyService;

    @Transactional
    public Map<String, Object> refundByPatient(Long billId, String reason) {
        AuthContext context = AuthContextHolder.require();
        if (!context.isPatient()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要患者身份");
        }
        return doRefund(billId, reason, context.getPatientId(), null, true);
    }

    @Transactional
    public Map<String, Object> refundByRegistrar(Long billId, String reason) {
        requireRegistrar();
        return doRefund(billId, reason, null, AuthContextHolder.require().getEmployeeId(), false);
    }

    @Transactional
    public Map<String, Object> refundRegisterBill(Long registerId, String reason, Long operatorEmployeeId) {
        Map<String, Object> bill = billRepository.findPaidByBiz(BillBizType.REGISTER, registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "未找到已支付的挂号账单"));
        return doRefund(((Number) bill.get("id")).longValue(), reason, null, operatorEmployeeId, false);
    }

    private Map<String, Object> doRefund(Long billId, String reason, Long patientId, Long operatorId,
                                         boolean patientInitiated) {
        Map<String, Object> bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账单不存在"));

        if (patientInitiated) {
            Long billPatientId = ((Number) bill.get("patientId")).longValue();
            if (!patientFamilyService.canAccessVisitPatient(patientId, billPatientId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该账单");
            }
        }

        if (((Number) bill.get("status")).intValue() != BillStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已支付账单可退费");
        }

        String bizType = (String) bill.get("bizType");
        Long bizId = ((Number) bill.get("bizId")).longValue();
        assertRefundable(bizType, bizId);

        Map<String, Object> paymentLink = paymentRepository.findPaymentLinkByBillId(billId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "未找到支付记录"));

        BigDecimal refundAmount = (BigDecimal) bill.get("amount");
        long refundId = refundRepository.insertRefund(
                ((Number) paymentLink.get("paymentId")).longValue(),
                billId,
                ((Number) bill.get("patientId")).longValue(),
                refundAmount,
                (String) paymentLink.get("channel"),
                operatorId,
                reason != null ? reason : "mock refund"
        );

        billRepository.markRefunded(billId);
        updateBizAfterRefund(bizType, bizId);

        Map<String, Object> result = new HashMap<>();
        result.put("refundId", refundId);
        result.put("billId", billId);
        result.put("refundAmount", refundAmount);
        result.put("bizType", bizType);
        result.put("message", "模拟退款成功");
        return result;
    }

    private void assertRefundable(String bizType, Long bizId) {
        switch (bizType) {
            case BillBizType.REGISTER -> {
                Map<String, Object> register = registerRepository.findByIdForUpdate(bizId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
                int visitState = ((Number) register.get("visitState")).intValue();
                if (visitState != VisitState.REGISTERED) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号未接诊状态可退挂号费，请使用退号");
                }
            }
            case BillBizType.INSPECTION -> {
                Map<String, Object> req = inspectionRequestRepository.findById(bizId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检验申请不存在"));
                if (((Number) req.get("status")).intValue() != InspectionRequestStatus.PAID) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "检验已执行或已退费，不可退款");
                }
            }
            case BillBizType.CHECK -> {
                Map<String, Object> req = checkRequestRepository.findById(bizId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));
                if (((Number) req.get("status")).intValue() != InspectionRequestStatus.PAID) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "检查已执行或已退费，不可退款");
                }
            }
            case BillBizType.PRESCRIPTION -> {
                Map<String, Object> rx = prescriptionRepository.findByIdForUpdate(bizId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));
                int status = ((Number) rx.get("status")).intValue();
                if (status != PrescriptionStatus.PAID && status != PrescriptionStatus.RETURNED) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "处方状态不允许退费");
                }
            }
            case BillBizType.DISPOSAL -> {
                Map<String, Object> req = disposalRequestRepository.findById(bizId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置申请不存在"));
                if (((Number) req.get("status")).intValue() != InspectionRequestStatus.PAID) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "处置已执行或已退费，不可退款");
                }
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的业务类型: " + bizType);
        }
    }

    private void updateBizAfterRefund(String bizType, Long bizId) {
        switch (bizType) {
            case BillBizType.REGISTER -> {
                registerRepository.updateVisitState(bizId, VisitState.CANCELLED);
                registerRepository.findById(bizId).ifPresent(reg -> {
                    if (reg.get("schedulingId") != null) {
                        schedulingRepository.decrementUsedQuota(((Number) reg.get("schedulingId")).longValue());
                    }
                });
            }
            case BillBizType.INSPECTION -> inspectionRequestRepository.updateStatus(bizId, InspectionRequestStatus.REFUNDED);
            case BillBizType.CHECK -> checkRequestRepository.updateStatus(bizId, InspectionRequestStatus.REFUNDED);
            case BillBizType.PRESCRIPTION -> prescriptionRepository.updateStatus(bizId, PrescriptionStatus.REFUNDED);
            case BillBizType.DISPOSAL -> disposalRequestRepository.updateStatus(bizId, InspectionRequestStatus.REFUNDED);
            default -> {
            }
        }
    }

    private void requireRegistrar() {
        AuthContext context = AuthContextHolder.require();
        if (!context.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要收费员身份");
        }
        List<String> roles = context.getRoles();
        if (roles == null || (!roles.contains("REGISTRAR") && !roles.contains("ADMIN"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要 REGISTRAR 角色");
        }
    }
}
