package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.DepartmentWriteRequest;
import com.hospital.management.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public Map<String, Object> create(DepartmentWriteRequest request) {
        if (!StringUtils.hasText(request.getDeptCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "科室编码不能为空");
        }
        try {
            long id = departmentRepository.insert(
                    request.getDeptCode().trim(),
                    request.getDeptName().trim(),
                    request.getDeptType(),
                    request.getSortNo() != null ? request.getSortNo() : 10);
            return departmentRepository.findById(id)
                    .map(row -> {
                        row.put("message", "科室已创建");
                        return row;
                    })
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "科室不存在"));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "科室编码已存在");
        }
    }

    public Map<String, Object> update(Long id, DepartmentWriteRequest request) {
        if (!departmentRepository.existsActive(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在");
        }
        if (departmentRepository.update(id,
                request.getDeptName().trim(),
                request.getDeptType(),
                request.getSortNo() != null ? request.getSortNo() : 10) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在");
        }
        Map<String, Object> row = departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "科室不存在"));
        row.put("message", "科室已更新");
        return row;
    }

    public Map<String, Object> delete(Long id) {
        if (!departmentRepository.existsActive(id)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在");
        }
        if (departmentRepository.countEmployees(id) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "科室下仍有在职员工，请先调整或停用");
        }
        if (departmentRepository.softDelete(id) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "科室不存在");
        }
        return Map.of("id", id, "message", "科室已停用");
    }
}
