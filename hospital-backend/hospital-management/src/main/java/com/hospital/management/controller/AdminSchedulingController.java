package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.management.dto.ApplyTemplateRequest;
import com.hospital.management.dto.BatchPublishRequest;
import com.hospital.management.dto.BatchUpsertRequest;
import com.hospital.management.dto.CopyWeekRequest;
import com.hospital.management.dto.SchedulingUpdateRequest;
import com.hospital.management.dto.SchedulingWriteRequest;
import com.hospital.management.dto.TemplateReplaceRequest;
import com.hospital.management.service.SchedulingService;
import com.hospital.management.service.SchedulingTemplateService;
import com.hospital.management.service.SchedulingWeekService;
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
    private final SchedulingWeekService schedulingWeekService;
    private final SchedulingTemplateService schedulingTemplateService;

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

    @GetMapping("/week-grid")
    public Result<Map<String, Object>> weekGrid(
            @RequestParam Long deptId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return Result.success(schedulingWeekService.getWeekGrid(deptId, weekStart));
    }

    @PostMapping("/batch-upsert")
    public Result<Map<String, Object>> batchUpsert(@Valid @RequestBody BatchUpsertRequest request) {
        return Result.success(schedulingWeekService.batchUpsert(request));
    }

    @PostMapping("/copy-week")
    public Result<Map<String, Object>> copyWeek(@Valid @RequestBody CopyWeekRequest request) {
        return Result.success(schedulingWeekService.copyWeek(request));
    }

    @PostMapping("/apply-template")
    public Result<Map<String, Object>> applyTemplate(@Valid @RequestBody ApplyTemplateRequest request) {
        return Result.success(schedulingWeekService.applyTemplate(request));
    }

    @PostMapping("/batch-publish")
    public Result<Map<String, Object>> batchPublish(@Valid @RequestBody BatchPublishRequest request) {
        return Result.success(schedulingWeekService.batchPublish(request));
    }

    @GetMapping("/templates")
    public Result<Map<String, Object>> listTemplates(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long employeeId) {
        return Result.success(schedulingTemplateService.listTemplates(deptId, employeeId));
    }

    @GetMapping("/templates/{employeeId}")
    public Result<Map<String, Object>> getTemplate(@PathVariable Long employeeId) {
        return Result.success(schedulingTemplateService.getTemplate(employeeId));
    }

    @PutMapping("/templates/{employeeId}")
    public Result<Map<String, Object>> replaceTemplate(@PathVariable Long employeeId,
                                                       @Valid @RequestBody TemplateReplaceRequest request) {
        return Result.success(schedulingTemplateService.replaceTemplate(employeeId, request));
    }
}
