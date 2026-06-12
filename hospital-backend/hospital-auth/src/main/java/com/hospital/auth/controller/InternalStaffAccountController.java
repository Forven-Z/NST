package com.hospital.auth.controller;

import com.hospital.auth.dto.StaffAccountCreateRequest;
import com.hospital.auth.dto.StaffAccountUpdateRequest;
import com.hospital.auth.service.StaffAccountService;
import com.hospital.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/staff/accounts")
@RequiredArgsConstructor
public class InternalStaffAccountController {

    private final StaffAccountService staffAccountService;

    @PostMapping
    public Result<Map<String, Object>> create(
            @Valid @RequestBody StaffAccountCreateRequest request,
            HttpServletRequest httpRequest) {
        return Result.success(staffAccountService.create(request, httpRequest));
    }

    @PutMapping("/{employeeId}")
    public Result<Map<String, Object>> update(
            @PathVariable Long employeeId,
            @RequestBody StaffAccountUpdateRequest request,
            HttpServletRequest httpRequest) {
        return Result.success(staffAccountService.update(employeeId, request, httpRequest));
    }

    @DeleteMapping("/{employeeId}")
    public Result<Map<String, Object>> disable(
            @PathVariable Long employeeId,
            HttpServletRequest httpRequest) {
        return Result.success(staffAccountService.disable(employeeId, httpRequest));
    }
}
