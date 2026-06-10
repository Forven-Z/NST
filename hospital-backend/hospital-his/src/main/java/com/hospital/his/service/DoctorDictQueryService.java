package com.hospital.his.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.his.repository.DiseaseRepository;
import com.hospital.his.repository.DrugRepository;
import com.hospital.his.repository.MedicalTechnologyRepository;
import com.hospital.his.security.AuthContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DoctorDictQueryService {

    private final DiseaseRepository diseaseRepository;
    private final MedicalTechnologyRepository medicalTechnologyRepository;
    private final DrugRepository drugRepository;

    public Map<String, Object> listDiseases(String keyword, int page, int pageSize) {
        requireDoctor();
        int offset = Math.max(page - 1, 0) * pageSize;
        return Map.of(
                "list", diseaseRepository.listDiseases(keyword, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    public Map<String, Object> listMedicalTechnologies(String keyword, String techType, int page, int pageSize) {
        requireDoctor();
        int offset = Math.max(page - 1, 0) * pageSize;
        return Map.of(
                "list", medicalTechnologyRepository.listMedicalTechnologies(keyword, techType, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    public Map<String, Object> listDrugs(String keyword, int page, int pageSize) {
        requireDoctor();
        int offset = Math.max(page - 1, 0) * pageSize;
        return Map.of(
                "list", drugRepository.listDrugs(keyword, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    private void requireDoctor() {
        if (AuthContextHolder.require().getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要门诊医生身份");
        }
    }
}
