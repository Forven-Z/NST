package com.hospital.his.service;



import com.hospital.common.constant.ErrorCode;

import com.hospital.common.exception.BusinessException;

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

    private final CheckReportQueryService checkReportQueryService;



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

        if (visitState != com.hospital.common.constant.VisitState.IN_CONSULTATION

                && visitState != com.hospital.common.constant.VisitState.REGISTERED) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "患者尚未进入可开单状态");

        }



        Map<String, Object> item = medicalTechnologyRepository.findCheckItem(request.getMedicalTechnologyId())

                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "检查项目不存在"));

        BigDecimal price = (BigDecimal) item.get("price");

        Long patientId = ((Number) register.get("patientId")).longValue();



        long checkId = checkRequestRepository.insert(

                request.getRegisterId(), patientId, request.getMedicalTechnologyId(), doctorId,

                price, request.getPurpose(), request.getBodyPart(), request.getRemark(),

                com.hospital.common.constant.InspectionRequestStatus.ORDERED);



        long billId = billRepository.insertBill(

                patientId, request.getRegisterId(), com.hospital.common.constant.BillBizType.CHECK, checkId,

                "检查费-" + item.get("itemName"), price);



        Map<String, Object> result = new HashMap<>();

        result.put("checkRequestId", checkId);

        result.put("status", com.hospital.common.constant.InspectionRequestStatus.ORDERED);

        result.put("billId", billId);

        result.put("amount", price);

        result.put("itemName", item.get("itemName"));

        return result;

    }



    public Map<String, Object> getCheckResult(Long checkRequestId) {

        Long doctorId = AuthContextHolder.require().getEmployeeId();

        return checkReportQueryService.getCheckReportForDoctor(checkRequestId, doctorId);

    }

}

