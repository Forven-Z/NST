package com.hospital.his.order.handler;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.order.MedTechOrderKind;
import com.hospital.his.order.state.OrderStatusCoordinator;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.CheckRequestRepository;
import com.hospital.his.repository.MedicalTechnologyRepository;
import com.hospital.his.repository.RegisterRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class CheckMedicalOrderHandler extends AbstractMedicalOrderHandler {

    private final CheckRequestRepository checkRequestRepository;

    public CheckMedicalOrderHandler(
            RegisterRepository registerRepository,
            MedicalTechnologyRepository medicalTechnologyRepository,
            BillRepository billRepository,
            OrderStatusCoordinator orderStatusCoordinator,
            CheckRequestRepository checkRequestRepository) {
        super(registerRepository, medicalTechnologyRepository, billRepository, orderStatusCoordinator);
        this.checkRequestRepository = checkRequestRepository;
    }

    @Override
    public String bizType() {
        return BillBizType.CHECK;
    }

    @Override
    protected MedTechOrderKind medTechKind() {
        return MedTechOrderKind.CHECK;
    }

    @Override
    protected Map<String, Object> loadTechItem(Long medicalTechnologyId) {
        return medicalTechnologyRepository.findCheckItem(medicalTechnologyId).orElse(null);
    }

    @Override
    protected long insertOrder(
            CreateInspectionRequest request,
            Long patientId,
            Long doctorId,
            BigDecimal price,
            Map<String, Object> item) {
        return checkRequestRepository.insert(
                request.getRegisterId(),
                patientId,
                request.getMedicalTechnologyId(),
                doctorId,
                price,
                request.getPurpose(),
                request.getBodyPart(),
                request.getRemark(),
                InspectionRequestStatus.ORDERED);
    }

    @Override
    protected Optional<Map<String, Object>> findOrderById(long orderId) {
        return checkRequestRepository.findById(orderId);
    }

    @Override
    protected String billTitlePrefix() {
        return "检查费-";
    }

    @Override
    protected String orderIdResultKey() {
        return "checkRequestId";
    }

    @Override
    protected String forbiddenOrderMessage() {
        return "只能为本队列患者开立检查";
    }

    @Override
    protected String itemNotFoundMessage() {
        return "检查项目不存在";
    }

    @Override
    protected String orderNotFoundMessage() {
        return "检查申请不存在";
    }

    @Override
    protected String notRefundableMessage() {
        return "检查已执行或已退费，不可退款";
    }
}
