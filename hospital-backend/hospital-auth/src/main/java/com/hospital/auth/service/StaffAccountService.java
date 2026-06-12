package com.hospital.auth.service;

import com.hospital.auth.config.AuthProperties;
import com.hospital.auth.dto.StaffAccountCreateRequest;
import com.hospital.auth.dto.StaffAccountUpdateRequest;
import com.hospital.auth.repository.StaffAccountRepository;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StaffAccountService {

    private final StaffAccountRepository staffAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public Map<String, Object> create(StaffAccountCreateRequest request, HttpServletRequest httpRequest) {
        assertInternalCaller(httpRequest);
        if (staffAccountRepository.existsByEmployeeId(request.getEmployeeId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该员工已有登录账号");
        }
        if (staffAccountRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已被占用");
        }
        String userType = "ADMIN".equals(request.getRoleType()) ? "ADMIN" : "STAFF";
        staffAccountRepository.insert(
                request.getEmployeeId(),
                request.getUsername().trim(),
                passwordEncoder.encode(request.getPassword()),
                userType);
        return Map.of("employeeId", request.getEmployeeId(), "username", request.getUsername().trim());
    }

    public Map<String, Object> update(Long employeeId, StaffAccountUpdateRequest request, HttpServletRequest httpRequest) {
        assertInternalCaller(httpRequest);
        if (!staffAccountRepository.existsByEmployeeId(employeeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工账号不存在");
        }
        if (request.getUsername() != null && staffAccountRepository.existsByUsername(request.getUsername())) {
            Optional<String> current = staffAccountRepository.findUsernameByEmployeeId(employeeId);
            if (current.isEmpty() || !current.get().equals(request.getUsername().trim())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已被占用");
            }
        }
        String hash = request.getPassword() != null && !request.getPassword().isBlank()
                ? passwordEncoder.encode(request.getPassword()) : null;
        staffAccountRepository.update(employeeId,
                request.getUsername() != null ? request.getUsername().trim() : null,
                hash,
                request.getStatus());
        return Map.of("employeeId", employeeId, "message", "账号已更新");
    }

    public Map<String, Object> disable(Long employeeId, HttpServletRequest httpRequest) {
        assertInternalCaller(httpRequest);
        if (staffAccountRepository.disableByEmployeeId(employeeId) == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "员工账号不存在");
        }
        return Map.of("employeeId", employeeId, "message", "账号已停用");
    }

    private void assertInternalCaller(HttpServletRequest request) {
        String headerName = authProperties.getInternal().getHeader();
        String caller = request.getHeader(headerName);
        if (!StringUtils.hasText(caller)
                || !authProperties.getInternal().getAllowedServices().contains(caller)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权调用内部接口");
        }
    }
}
