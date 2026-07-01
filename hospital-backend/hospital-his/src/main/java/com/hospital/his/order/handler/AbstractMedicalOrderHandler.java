package com.hospital.his.order.handler;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.client.PatientBillBridge;
import com.hospital.his.order.MedTechOrderKind;
import com.hospital.his.order.state.OrderStatusCoordinator;
import com.hospital.his.repository.MedicalTechnologyRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 医技开单模板（检验 / 检查 / 处置）：固定校验 → 查项目 → insert 医嘱 → insert 账单。
 */
public abstract class AbstractMedicalOrderHandler implements MedicalOrderHandler {

    protected final RegisterRepository registerRepository;
    protected final MedicalTechnologyRepository medicalTechnologyRepository;
    protected final PatientBillBridge patientBillBridge;
    protected final OrderStatusCoordinator orderStatusCoordinator;

    protected AbstractMedicalOrderHandler(
            RegisterRepository registerRepository,
            MedicalTechnologyRepository medicalTechnologyRepository,
            PatientBillBridge patientBillBridge,
            OrderStatusCoordinator orderStatusCoordinator) {
        this.registerRepository = registerRepository;
        this.medicalTechnologyRepository = medicalTechnologyRepository;
        this.patientBillBridge = patientBillBridge;
        this.orderStatusCoordinator = orderStatusCoordinator;
    }

    protected abstract MedTechOrderKind medTechKind();

    protected abstract Map<String, Object> loadTechItem(Long medicalTechnologyId);

    protected abstract long insertOrder(
            CreateInspectionRequest request,
            Long patientId,
            Long doctorId,
            BigDecimal price,
            Map<String, Object> item);

    protected abstract Optional<Map<String, Object>> findOrderById(long orderId);

    protected abstract String billTitlePrefix();

    protected abstract String orderIdResultKey();

    protected abstract String forbiddenOrderMessage();

    protected abstract String itemNotFoundMessage();

    protected abstract String orderNotFoundMessage();

    protected abstract String notRefundableMessage();

    protected String optionalSuccessMessage() {
        return null;
    }

    @Override
    @Transactional
    public Map<String, Object> createOrder(OrderCreateContext ctx) {
        CreateInspectionRequest request = ctx.medTechRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "医技开单参数缺失");
        }
        return createMedTechOrder(request);
    }

    @Transactional
    protected Map<String, Object> createMedTechOrder(CreateInspectionRequest request) {
        Long doctorId = requireDoctorId();

        Map<String, Object> register = registerRepository.findById(request.getRegisterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        if (!doctorId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, forbiddenOrderMessage());
        }
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.IN_CONSULTATION && visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "患者尚未进入可开单状态");
        }

        Map<String, Object> item = loadTechItem(request.getMedicalTechnologyId());
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, itemNotFoundMessage());
        }
        BigDecimal price = (BigDecimal) item.get("price");
        Long patientId = ((Number) register.get("patientId")).longValue();

        long orderId = insertOrder(request, patientId, doctorId, price, item);

        long billId = patientBillBridge.createBill(
                patientId,
                request.getRegisterId(),
                bizType(),
                orderId,
                billTitlePrefix() + item.get("itemName"),
                price);

        Map<String, Object> result = new HashMap<>();
        result.put(orderIdResultKey(), orderId);
        result.put("status", InspectionRequestStatus.ORDERED);
        result.put("billId", billId);
        result.put("amount", price);
        result.put("itemName", item.get("itemName"));
        String message = optionalSuccessMessage();
        if (message != null) {
            result.put("message", message);
        }
        return result;
    }

    @Override
    @Transactional
    public void onBillPaid(long bizId) {
        orderStatusCoordinator.payMedTechOrder(medTechKind(), bizId);
    }

    @Override
    @Transactional
    public void onRefund(long bizId) {
        orderStatusCoordinator.refundMedTechOrder(medTechKind(), bizId);
    }

    @Override
    public void assertBillRefundable(long bizId) {
        Map<String, Object> req = findOrderById(bizId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, orderNotFoundMessage()));
        if (((Number) req.get("status")).intValue() != InspectionRequestStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, notRefundableMessage());
        }
    }

    protected Long requireDoctorId() {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        if (doctorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }
        return doctorId;
    }
}
