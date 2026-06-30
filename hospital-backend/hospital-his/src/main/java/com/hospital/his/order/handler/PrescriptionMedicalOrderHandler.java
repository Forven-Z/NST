package com.hospital.his.order.handler;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.BillStatus;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.PrescriptionStatus;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.doctor.CreatePrescriptionRequest;
import com.hospital.his.dto.doctor.UpdatePrescriptionRequest;
import com.hospital.his.order.state.OrderStatusCoordinator;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.DrugRepository;
import com.hospital.his.repository.PrescriptionRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PrescriptionMedicalOrderHandler implements MedicalOrderHandler {

    private final RegisterRepository registerRepository;
    private final DrugRepository drugRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final BillRepository billRepository;
    private final OrderStatusCoordinator orderStatusCoordinator;

    public PrescriptionMedicalOrderHandler(
            RegisterRepository registerRepository,
            DrugRepository drugRepository,
            PrescriptionRepository prescriptionRepository,
            BillRepository billRepository,
            OrderStatusCoordinator orderStatusCoordinator) {
        this.registerRepository = registerRepository;
        this.drugRepository = drugRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.billRepository = billRepository;
        this.orderStatusCoordinator = orderStatusCoordinator;
    }

    @Override
    public String bizType() {
        return BillBizType.PRESCRIPTION;
    }

    @Override
    @Transactional
    public Map<String, Object> createOrder(OrderCreateContext ctx) {
        CreatePrescriptionRequest request = ctx.prescriptionRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "处方开单参数缺失");
        }
        Long doctorId = requireDoctorId();
        Map<String, Object> register = loadRegisterForDoctor(request.getRegisterId(), doctorId);
        Long patientId = ((Number) register.get("patientId")).longValue();

        ItemBuildResult built = buildItemSnapshots(request.getItems());
        orderStatusCoordinator.onPrescriptionOrdered(built.snapshots());
        long prescriptionId = prescriptionRepository.insertPrescription(
                request.getRegisterId(), patientId, doctorId, built.totalAmount(), PrescriptionStatus.ORDERED);
        persistItems(prescriptionId, built.snapshots());

        long billId = billRepository.insertBill(
                patientId, request.getRegisterId(), BillBizType.PRESCRIPTION, prescriptionId,
                "处方费 #" + prescriptionId, built.totalAmount());

        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionId", prescriptionId);
        result.put("totalAmount", built.totalAmount());
        result.put("status", PrescriptionStatus.ORDERED);
        result.put("billId", billId);
        return result;
    }

    @Transactional
    public Map<String, Object> updateRejectedPrescription(Long prescriptionId, UpdatePrescriptionRequest request) {
        Long doctorId = requireDoctorId();
        Map<String, Object> prescription = loadRejectedPrescriptionForDoctor(prescriptionId, doctorId);

        ItemBuildResult built = buildItemSnapshots(request.getItems());
        prescriptionRepository.deleteItemsByPrescriptionId(prescriptionId);
        persistItems(prescriptionId, built.snapshots());
        if (prescriptionRepository.updateTotalAmount(prescriptionId, built.totalAmount()) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "处方不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionId", prescriptionId);
        result.put("totalAmount", built.totalAmount());
        result.put("status", PrescriptionStatus.PHARMACY_REJECTED);
        result.put("rejectReason", prescription.get("rejectReason"));
        return result;
    }

    @Transactional
    public Map<String, Object> resubmitPrescription(Long prescriptionId) {
        Long doctorId = requireDoctorId();
        Map<String, Object> prescription = loadRejectedPrescriptionForDoctor(prescriptionId, doctorId);
        BigDecimal totalAmount = (BigDecimal) prescription.get("totalAmount");
        Long patientId = ((Number) prescription.get("patientId")).longValue();
        Long registerId = ((Number) prescription.get("registerId")).longValue();

        orderStatusCoordinator.resubmitPrescription(prescriptionId, totalAmount);

        String billTitle = "处方费 #" + prescriptionId;
        long billId = billRepository.findByBiz(BillBizType.PRESCRIPTION, prescriptionId)
                .map(existing -> {
                    int status = ((Number) existing.get("status")).intValue();
                    if (status != BillStatus.REFUNDED) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "处方账单状态异常，无法重新提交");
                    }
                    Long existingBillId = ((Number) existing.get("id")).longValue();
                    if (billRepository.resetForResubmit(existingBillId, billTitle, totalAmount) == 0) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "处方账单重置失败");
                    }
                    return existingBillId;
                })
                .orElseGet(() -> billRepository.insertBill(
                        patientId, registerId, BillBizType.PRESCRIPTION, prescriptionId,
                        billTitle, totalAmount));

        Map<String, Object> result = new HashMap<>();
        result.put("prescriptionId", prescriptionId);
        result.put("totalAmount", totalAmount);
        result.put("status", PrescriptionStatus.ORDERED);
        result.put("billId", billId);
        result.put("message", "处方已重新提交，请通知患者缴费");
        return result;
    }

    @Override
    @Transactional
    public void onBillPaid(long bizId) {
        orderStatusCoordinator.payPrescription(bizId);
    }

    @Override
    @Transactional
    public void onRefund(long bizId) {
        orderStatusCoordinator.refundPrescription(bizId);
    }

    @Override
    public void assertBillRefundable(long bizId) {
        Map<String, Object> rx = prescriptionRepository.findByIdForUpdate(bizId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));
        int status = ((Number) rx.get("status")).intValue();
        if (status != PrescriptionStatus.PAID && status != PrescriptionStatus.RETURNED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "处方状态不允许退费");
        }
    }

    private Long requireDoctorId() {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        if (doctorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }
        return doctorId;
    }

    private Map<String, Object> loadRegisterForDoctor(Long registerId, Long doctorId) {
        Map<String, Object> register = registerRepository.findById(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        if (!doctorId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为本队列患者开立处方");
        }
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.IN_CONSULTATION && visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "患者尚未进入可开单状态");
        }
        return register;
    }

    private Map<String, Object> loadRejectedPrescriptionForDoctor(Long prescriptionId, Long doctorId) {
        Map<String, Object> prescription = prescriptionRepository.findDetailById(prescriptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处方不存在"));
        if (!doctorId.equals(((Number) prescription.get("doctorId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改本人驳回的处方");
        }
        if (((Number) prescription.get("status")).intValue() != PrescriptionStatus.PHARMACY_REJECTED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅药师驳回处方可修改");
        }
        return prescription;
    }

    private ItemBuildResult buildItemSnapshots(List<CreatePrescriptionRequest.PrescriptionItemRequest> items) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sortNo = 0;
        for (CreatePrescriptionRequest.PrescriptionItemRequest item : items) {
            Map<String, Object> drug = drugRepository.findByIdForUpdate(item.getDrugId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "药品不存在: " + item.getDrugId()));
            BigDecimal unitPrice = (BigDecimal) drug.get("retailPrice");
            BigDecimal amount = unitPrice.multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(amount);

            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("drug", drug);
            snapshot.put("item", item);
            snapshot.put("amount", amount);
            snapshot.put("sortNo", sortNo++);
            snapshots.add(snapshot);
        }
        return new ItemBuildResult(snapshots, totalAmount);
    }

    private void persistItems(long prescriptionId, List<Map<String, Object>> snapshots) {
        for (Map<String, Object> snapshot : snapshots) {
            @SuppressWarnings("unchecked")
            Map<String, Object> drug = (Map<String, Object>) snapshot.get("drug");
            CreatePrescriptionRequest.PrescriptionItemRequest item =
                    (CreatePrescriptionRequest.PrescriptionItemRequest) snapshot.get("item");
            prescriptionRepository.insertItem(
                    prescriptionId,
                    item.getDrugId(),
                    (String) drug.get("drugCode"),
                    (String) drug.get("drugName"),
                    (String) drug.get("drugFormat"),
                    (String) drug.get("drugDosage"),
                    (String) drug.get("drugType"),
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
    }

    private record ItemBuildResult(List<Map<String, Object>> snapshots, BigDecimal totalAmount) {
    }
}
