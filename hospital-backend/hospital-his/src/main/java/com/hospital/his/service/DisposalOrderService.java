package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.DisposalRequestRepository;
import com.hospital.his.repository.MedicalTechnologyRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DisposalOrderService {

    private final RegisterRepository registerRepository;
    private final MedicalTechnologyRepository medicalTechnologyRepository;
    private final DisposalRequestRepository disposalRequestRepository;
    private final BillRepository billRepository;
    private final DisposalRecordQueryService disposalRecordQueryService;

    @Transactional
    public Map<String, Object> createDisposalOrder(CreateInspectionRequest request) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        if (doctorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }

        Map<String, Object> register = registerRepository.findById(request.getRegisterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        if (!doctorId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为本队列患者开立处置");
        }
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.IN_CONSULTATION && visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "患者尚未进入可开单状态");
        }

        Map<String, Object> item = medicalTechnologyRepository.findDisposalItem(request.getMedicalTechnologyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "处置项目不存在"));
        BigDecimal price = (BigDecimal) item.get("price");
        Long patientId = ((Number) register.get("patientId")).longValue();

        long disposalId = disposalRequestRepository.insert(
                request.getRegisterId(),
                patientId,
                request.getMedicalTechnologyId(),
                doctorId,
                price,
                request.getPurpose(),
                request.getBodyPart(),
                request.getRemark(),
                InspectionRequestStatus.ORDERED
        );

        long billId = billRepository.insertBill(
                patientId,
                request.getRegisterId(),
                BillBizType.DISPOSAL,
                disposalId,
                "处置费-" + item.get("itemName"),
                price
        );

        Map<String, Object> result = new HashMap<>();
        result.put("disposalRequestId", disposalId);
        result.put("status", InspectionRequestStatus.ORDERED);
        result.put("billId", billId);
        result.put("amount", price);
        result.put("itemName", item.get("itemName"));
        result.put("message", "已开立处置，请患者缴费后至处置科");
        return result;
    }

    public Map<String, Object> getDisposalResult(Long disposalRequestId) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        return disposalRecordQueryService.getDisposalRecordForDoctor(disposalRequestId, doctorId);
    }
}
