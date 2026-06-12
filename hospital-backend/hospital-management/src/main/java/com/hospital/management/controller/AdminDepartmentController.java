package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.management.dto.DepartmentWriteRequest;
import com.hospital.management.service.DepartmentService;
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
@RequestMapping("/api/v1/admin/departments")
@RequiredArgsConstructor
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody DepartmentWriteRequest request) {
        return Result.success(departmentService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @Valid @RequestBody DepartmentWriteRequest request) {
        return Result.success(departmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        return Result.success(departmentService.delete(id));
    }
}
