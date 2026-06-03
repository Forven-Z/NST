package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.repository.DictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.BiFunction;

@Service
@RequiredArgsConstructor
public class DictReadService {

    private final DictRepository dictRepository;

    public Map<String, Object> listDepartments(String keyword, int page, int pageSize) {
        return pageResult(keyword, page, pageSize, dictRepository::listDepartments);
    }

    public Map<String, Object> listRegistLevels(String keyword, int page, int pageSize) {
        return pageResult(keyword, page, pageSize, dictRepository::listRegistLevels);
    }

    public Map<String, Object> listSettleCategories(String keyword, int page, int pageSize) {
        return pageResult(keyword, page, pageSize, dictRepository::listSettleCategories);
    }

    public Map<String, Object> listMedicalTechnologies(String keyword, String techType, int page, int pageSize) {
        int offset = Math.max(page - 1, 0) * pageSize;
        return Map.of(
                "list", dictRepository.listMedicalTechnologies(keyword, techType, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    public Map<String, Object> listDrugs(String keyword, int page, int pageSize) {
        return pageResult(keyword, page, pageSize, dictRepository::listDrugs);
    }

    public Map<String, Object> listDiseases(String keyword, int page, int pageSize) {
        return pageResult(keyword, page, pageSize, dictRepository::listDiseases);
    }

    public Map<String, Object> getDepartment(Long id) {
        return dictRepository.findDepartmentById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "科室不存在"));
    }

    private Map<String, Object> pageResult(String keyword, int page, int pageSize,
                                           TriFunction<String, Integer, Integer, java.util.List<Map<String, Object>>> loader) {
        int offset = Math.max(page - 1, 0) * pageSize;
        return Map.of(
                "list", loader.apply(keyword, offset, pageSize),
                "page", page,
                "pageSize", pageSize
        );
    }

    @FunctionalInterface
    private interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }
}
