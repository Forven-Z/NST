package com.hospital.management.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.management.dto.EmployeeWriteRequest;
import com.hospital.management.repository.DepartmentRepository;
import com.hospital.management.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeePersistenceService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long insertEmployee(EmployeeWriteRequest req) {
        if (!departmentRepository.existsActive(req.getDeptId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "科室不存在");
        }
        try {
            return employeeRepository.insert(
                    req.getEmpNo().trim(),
                    req.getRealName().trim(),
                    req.getGender(),
                    req.getDeptId(),
                    req.getTitle(),
                    req.getRoleType(),
                    req.getPhone());
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工号已存在");
        }
    }

    /** 独立事务物理删除，用于账号创建失败时补偿（insert 已 REQUIRES_NEW 提交，外层回滚删不掉）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByIdInNewTransaction(long employeeId) {
        employeeRepository.deleteById(employeeId);
    }
}
