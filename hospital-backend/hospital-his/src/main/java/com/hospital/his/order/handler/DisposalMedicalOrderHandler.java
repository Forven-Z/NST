package com.hospital.his.order.handler;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.order.MedTechOrderKind;
import com.hospital.his.order.state.OrderStatusCoordinator;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.DisposalRequestRepository;
import com.hospital.his.repository.MedicalTechnologyRepository;
import com.hospital.his.repository.RegisterRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class DisposalMedicalOrderHandler extends AbstractMedicalOrderHandler {

    private final DisposalRequestRepository disposalRequestRepository;

    public DisposalMedicalOrderHandler(
            RegisterRepository registerRepository,
            MedicalTechnologyRepository medicalTechnologyRepository,
            BillRepository billRepository,
            OrderStatusCoordinator orderStatusCoordinator,
            DisposalRequestRepository disposalRequestRepository) {
        super(registerRepository, medicalTechnologyRepository, billRepository, orderStatusCoordinator);
        this.disposalRequestRepository = disposalRequestRepository;
    }

    @Override
    public String bizType() {
        return BillBizType.DISPOSAL;
    }

    @Override
    protected MedTechOrderKind medTechKind() {
        return MedTechOrderKind.DISPOSAL;
    }

    @Override
    protected Map<String, Object> loadTechItem(Long medicalTechnologyId) {
        return medicalTechnologyRepository.findDisposalItem(medicalTechnologyId).orElse(null);
    }

    @Override
    protected long insertOrder(
            CreateInspectionRequest request,
            Long patientId,
            Long doctorId,
            BigDecimal price,
            Map<String, Object> item) {
        return disposalRequestRepository.insert(
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
        return disposalRequestRepository.findById(orderId);
    }

    @Override
    protected String billTitlePrefix() {
        return "处置费-";
    }

    @Override
    protected String orderIdResultKey() {
        return "disposalRequestId";
    }

    @Override
    protected String forbiddenOrderMessage() {
        return "只能为本队列患者开立处置";
    }

    @Override
    protected String itemNotFoundMessage() {
        return "处置项目不存在";
    }

    @Override
    protected String orderNotFoundMessage() {
        return "处置申请不存在";
    }

    @Override
    protected String notRefundableMessage() {
        return "处置已执行或已退费，不可退款";
    }

    @Override
    protected String optionalSuccessMessage() {
        return "已开立处置，请患者缴费后至处置科";
    }
}
