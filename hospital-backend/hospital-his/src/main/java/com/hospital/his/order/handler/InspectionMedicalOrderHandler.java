package com.hospital.his.order.handler;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.order.MedTechOrderKind;
import com.hospital.his.client.PatientBillBridge;
import com.hospital.his.order.state.OrderStatusCoordinator;
import com.hospital.his.repository.InspectionRequestRepository;
import com.hospital.his.repository.MedicalTechnologyRepository;
import com.hospital.his.repository.RegisterRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class InspectionMedicalOrderHandler extends AbstractMedicalOrderHandler {

    private final InspectionRequestRepository inspectionRequestRepository;

    public InspectionMedicalOrderHandler(
            RegisterRepository registerRepository,
            MedicalTechnologyRepository medicalTechnologyRepository,
            PatientBillBridge patientBillBridge,
            OrderStatusCoordinator orderStatusCoordinator,
            InspectionRequestRepository inspectionRequestRepository) {
        super(registerRepository, medicalTechnologyRepository, patientBillBridge, orderStatusCoordinator);
        this.inspectionRequestRepository = inspectionRequestRepository;
    }

    @Override
    public String bizType() {
        return BillBizType.INSPECTION;
    }

    @Override
    protected MedTechOrderKind medTechKind() {
        return MedTechOrderKind.INSPECTION;
    }

    @Override
    protected Map<String, Object> loadTechItem(Long medicalTechnologyId) {
        return medicalTechnologyRepository.findInspectionItem(medicalTechnologyId).orElse(null);
    }

    @Override
    protected long insertOrder(
            CreateInspectionRequest request,
            Long patientId,
            Long doctorId,
            BigDecimal price,
            Map<String, Object> item) {
        return inspectionRequestRepository.insert(
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
        return inspectionRequestRepository.findById(orderId);
    }

    @Override
    protected String billTitlePrefix() {
        return "检验费-";
    }

    @Override
    protected String orderIdResultKey() {
        return "inspectionRequestId";
    }

    @Override
    protected String forbiddenOrderMessage() {
        return "只能为本队列患者开立检验";
    }

    @Override
    protected String itemNotFoundMessage() {
        return "检验项目不存在";
    }

    @Override
    protected String orderNotFoundMessage() {
        return "检验申请不存在";
    }

    @Override
    protected String notRefundableMessage() {
        return "检验已执行或已退费，不可退款";
    }
}
