package com.hospital.patient.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.patient.client.ClinicalOrderBridge;
import com.hospital.patient.clinicalsync.ClinicalSyncService;
import com.hospital.patient.repository.BillRepository;
import com.hospital.patient.repository.PatientRepository;
import com.hospital.patient.repository.PaymentRepository;
import com.hospital.patient.repository.RefundRepository;
import com.hospital.patient.repository.RegisterRepository;
import com.hospital.patient.repository.SchedulingRepository;
import com.hospital.patient.security.AuthContext;
import com.hospital.patient.security.AuthContextHolder;
import com.hospital.patient.visit.PatientVisitLifecycleCoordinator;
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
    private final SchedulingRepository schedulingRepository;
    private final PatientRepository patientRepository;
    private final PatientFamilyService patientFamilyService;
    private final RegisterLifecycleService registerLifecycleService;
    private final PatientVisitLifecycleCoordinator visitLifecycleCoordinator;
    private final ClinicalOrderBridge clinicalOrderBridge;
    private final ClinicalSyncService clinicalSyncService;

    @Transactional
    public Map<String, Object> refundByPatient(Long billId, String reason) {
        AuthContext context = AuthContextHolder.require();
        if (!context.isPatient()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要患者身份");
        }
        return doRefund(billId, reason, context.getPatientId(), null, true, true);
    }

    @Transactional
    public Map<String, Object> refundByRegistrar(Long billId, String reason) {
        requireRegistrar();
        return doRefund(billId, reason, null, AuthContextHolder.require().getEmployeeId(), false, true);
    }

    @Transactional
    public Map<String, Object> refundRegisterBill(Long registerId, String reason, Long operatorEmployeeId) {
        Map<String, Object> bill = billRepository.findPaidByBiz(BillBizType.REGISTER, registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "未找到已支付的挂号账单"));
        return doRefund(((Number) bill.get("id")).longValue(), reason, null, operatorEmployeeId, false, true);
    }

    @Transactional
    public Map<String, Object> refundPrescriptionBillForPharmacyReject(
            Long prescriptionId, String reason, Long operatorPharmacistId) {
        return billRepository.findPaidByBiz(BillBizType.PRESCRIPTION, prescriptionId)
                .map(bill -> doRefund(
                        ((Number) bill.get("id")).longValue(),
                        reason,
                        null,
                        operatorPharmacistId,
                        false,
                        false))
                .orElseGet(() -> idempotentPharmacyRejectRefund(prescriptionId));
    }

    private Map<String, Object> idempotentPharmacyRejectRefund(Long prescriptionId) {
        Map<String, Object> bill = billRepository.findByBiz(BillBizType.PRESCRIPTION, prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "未找到已支付的处方账单"));
        int billStatus = ((Number) bill.get("status")).intValue();
        if (billStatus != BillStatus.REFUNDED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未找到已支付的处方账单");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("billId", bill.get("id"));
        result.put("prescriptionId", prescriptionId);
        result.put("refundAmount", bill.get("amount"));
        result.put("bizType", BillBizType.PRESCRIPTION);
        result.put("idempotent", true);
        result.put("message", "账单已退费，可继续完成处方驳回");
        return result;
    }

    private Map<String, Object> doRefund(Long billId, String reason, Long patientId, Long operatorId,
                                         boolean patientInitiated, boolean updateBizStatus) {
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
                reason != null ? reason : "窗口退费"
        );

        billRepository.markRefunded(billId);
        List<Long> clinicalSyncTaskIds = clinicalSyncService.newTaskIdList();
        if (updateBizStatus) {
            enqueueClinicalRefundIfNeeded(bizType, bizId, clinicalSyncTaskIds);
            updateBizAfterRefundLocally(bizType, bizId, bill);
        }
        clinicalSyncService.scheduleProcessAfterCommit(clinicalSyncTaskIds);

        if (BillBizType.REGISTER.equals(bizType)) {
            refundPaidMedicalBookBillIfPresent(bizId, reason, operatorId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("refundId", refundId);
        result.put("billId", billId);
        result.put("refundAmount", refundAmount);
        result.put("bizType", bizType);
        result.put("message", "模拟退款成功");
        return result;
    }

    private void assertRefundable(String bizType, Long bizId) {
        if (clinicalOrderBridge.handles(bizType)) {
            clinicalOrderBridge.assertBillRefundable(bizType, bizId);
            return;
        }
        switch (bizType) {
            case BillBizType.REGISTER -> {
                Map<String, Object> register = registerRepository.findByIdForUpdate(bizId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
                registerLifecycleService.assertRegisterBillRefundable(register);
            }
            case BillBizType.MEDICAL_BOOK -> {
                Map<String, Object> register = registerRepository.findById(bizId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
                int visitState = ((Number) register.get("visitState")).intValue();
                if (visitState == VisitState.IN_CONSULTATION || visitState == VisitState.FINISHED) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "就诊中或已结束，不可退病历本费");
                }
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的业务类型: " + bizType);
        }
    }

    private void enqueueClinicalRefundIfNeeded(String bizType, Long bizId, List<Long> clinicalSyncTaskIds) {
        if (!clinicalOrderBridge.handles(bizType)) {
            return;
        }
        clinicalSyncTaskIds.add(clinicalSyncService.enqueueOnRefund(bizType, bizId));
    }

    private void updateBizAfterRefundLocally(String bizType, Long bizId, Map<String, Object> bill) {
        if (clinicalOrderBridge.handles(bizType)) {
            return;
        }
        switch (bizType) {
            case BillBizType.REGISTER -> {
                visitLifecycleCoordinator.cancelRegistered(bizId);
                registerRepository.findById(bizId).ifPresent(reg -> {
                    if (reg.get("schedulingId") != null) {
                        schedulingRepository.decrementUsedQuota(((Number) reg.get("schedulingId")).longValue());
                    }
                    BigDecimal registFee = (BigDecimal) reg.get("registFee");
                    BigDecimal billAmount = (BigDecimal) bill.get("amount");
                    if (registFee != null && billAmount.compareTo(registFee) > 0) {
                        long patientId = ((Number) bill.get("patientId")).longValue();
                        patientRepository.updateNeedMedicalBook(patientId, false);
                    }
                });
            }
            case BillBizType.MEDICAL_BOOK -> {
                long patientId = ((Number) bill.get("patientId")).longValue();
                patientRepository.updateNeedMedicalBook(patientId, false);
            }
            default -> {
            }
        }
    }

    public void refundPaidMedicalBookBillIfPresent(Long registerId, String reason, Long operatorId) {
        billRepository.findPaidByBiz(BillBizType.MEDICAL_BOOK, registerId).ifPresent(bill ->
                doRefund(((Number) bill.get("id")).longValue(), reason, null, operatorId, false, true));
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
