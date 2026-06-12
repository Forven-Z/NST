package com.hospital.his.service;

import com.hospital.common.constant.BillBizType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.InspectionRequestStatus;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.common.support.MedTechReportSupport;
import com.hospital.common.support.MedTechReportSupport.ParsedPublishedText;
import com.hospital.his.dto.doctor.CreateInspectionRequest;
import com.hospital.his.repository.BillRepository;
import com.hospital.his.repository.CheckRequestRepository;
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
public class CheckOrderService {

    private final RegisterRepository registerRepository;
    private final MedicalTechnologyRepository medicalTechnologyRepository;
    private final CheckRequestRepository checkRequestRepository;
    private final BillRepository billRepository;

    @Transactional
    public Map<String, Object> createCheckOrder(CreateInspectionRequest request) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        if (doctorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }

        Map<String, Object> register = registerRepository.findById(request.getRegisterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));
        if (!doctorId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为本队列患者开立检查");
        }
        int visitState = ((Number) register.get("visitState")).intValue();
        if (visitState != VisitState.IN_CONSULTATION && visitState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "患者尚未进入可开单状态");
        }

        Map<String, Object> item = medicalTechnologyRepository.findCheckItem(request.getMedicalTechnologyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查项目不存在"));
        BigDecimal price = (BigDecimal) item.get("price");
        Long patientId = ((Number) register.get("patientId")).longValue();

        long checkId = checkRequestRepository.insert(
                request.getRegisterId(), patientId, request.getMedicalTechnologyId(), doctorId,
                price, request.getPurpose(), request.getBodyPart(), request.getRemark(),
                InspectionRequestStatus.ORDERED);

        long billId = billRepository.insertBill(
                patientId, request.getRegisterId(), BillBizType.CHECK, checkId,
                "检查费-" + item.get("itemName"), price);

        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", checkId);
        result.put("status", InspectionRequestStatus.ORDERED);
        result.put("billId", billId);
        result.put("amount", price);
        result.put("itemName", item.get("itemName"));
        return result;
    }

    public Map<String, Object> getCheckResult(Long checkRequestId) {
        Long doctorId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> row = checkRequestRepository.findByIdAndDoctor(checkRequestId, doctorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查申请不存在"));
        int status = ((Number) row.get("status")).intValue();
        if (status < InspectionRequestStatus.RESULT_READY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检查结果尚未出具");
        }
        String itemName = (String) row.get("itemName");
        String resultText = row.get("resultText") != null ? String.valueOf(row.get("resultText")) : "";
        ParsedPublishedText parsed = MedTechReportSupport.parsePublishedText(resultText);

        Map<String, Object> result = new HashMap<>();
        result.put("checkRequestId", row.get("checkRequestId"));
        result.put("itemName", itemName);
        result.put("status", status);
        result.put("resultText", resultText);
        result.put("resultTime", row.get("resultTime"));
        result.put("reportTime", row.get("resultTime"));
        result.put("instrumentData", MedTechReportSupport.instrumentDataFor(itemName));
        result.put("aiReportText", parsed.aiReportText());
        result.put("doctorReportText", parsed.doctorReportText());
        result.put("aiReportStatus",
                !parsed.aiReportText().isBlank() || !parsed.doctorReportText().isBlank() ? "READY" : "PENDING");
        return result;
    }
}
