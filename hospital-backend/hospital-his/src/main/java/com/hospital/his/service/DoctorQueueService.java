package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.constant.VisitState;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.MedicalRecordRepository;
import com.hospital.his.repository.RegisterRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DoctorQueueService {

    private static final int MEDICAL_RECORD_SUBMITTED = 2;

    private final RegisterRepository registerRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public Map<String, Object> listQueue(Integer visitState, String keyword, int page, int pageSize) {
        Long employeeId = AuthContextHolder.require().getEmployeeId();
        if (employeeId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号未绑定员工信息");
        }

        int offset = Math.max(page - 1, 0) * pageSize;
        List<Map<String, Object>> rawList = registerRepository.findDoctorQueue(
                employeeId, visitState, keyword, offset, pageSize);

        List<Map<String, Object>> list = rawList.stream().map(row -> {
            Map<String, Object> item = new HashMap<>(row);
            Object birthDate = row.get("birthDate");
            if (birthDate instanceof LocalDate bd) {
                item.put("age", Period.between(bd, LocalDate.now()).getYears());
            }
            item.remove("birthDate");
            return item;
        }).toList();

        return Map.of("list", list, "page", page, "pageSize", pageSize);
    }

    @Transactional
    public Map<String, Object> callPatient(Long registerId) {
        Long employeeId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> register = registerRepository.findById(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));

        if (!employeeId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权叫号该患者");
        }

        int currentState = ((Number) register.get("visitState")).intValue();
        if (currentState != VisitState.REGISTERED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅已挂号状态可叫号");
        }

        registerRepository.markCalled(registerId);
        return Map.of("registerId", registerId, "visitState", VisitState.IN_CONSULTATION);
    }

    @Transactional
    public Map<String, Object> finishVisit(Long registerId) {
        Long employeeId = AuthContextHolder.require().getEmployeeId();
        Map<String, Object> register = registerRepository.findById(registerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "挂号记录不存在"));

        if (!employeeId.equals(((Number) register.get("employeeId")).longValue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该挂号");
        }

        int currentState = ((Number) register.get("visitState")).intValue();
        if (currentState == VisitState.FINISHED) {
            return Map.of("registerId", registerId, "visitState", VisitState.FINISHED);
        }
        if (currentState != VisitState.IN_CONSULTATION) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅接诊中状态可结束看诊");
        }

        int recordStatus = medicalRecordRepository.findStatusByRegisterId(registerId).orElse(0);
        if (recordStatus != MEDICAL_RECORD_SUBMITTED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先确诊提交病历后再结束看诊");
        }

        registerRepository.markFinished(registerId);
        return Map.of("registerId", registerId, "visitState", VisitState.FINISHED);
    }
}
