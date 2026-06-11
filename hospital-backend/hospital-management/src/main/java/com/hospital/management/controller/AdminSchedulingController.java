package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.management.dto.SchedulingUpdateRequest;
import com.hospital.management.dto.SchedulingWriteRequest;
import com.hospital.management.service.SchedulingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/scheduling")
@RequiredArgsConstructor
public class AdminSchedulingController {

    private final SchedulingService schedulingService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam(required = false) Integer publishStatus) {
        return Result.success(schedulingService.list(deptId, employeeId, workDate, publishStatus));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody SchedulingWriteRequest request) {
        return Result.success(schedulingService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @RequestBody SchedulingUpdateRequest request) {
        return Result.success(schedulingService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public Result<Map<String, Object>> publish(@PathVariable Long id) {
        return Result.success(schedulingService.publish(id));
    }
}
