package com.hospital.lis.controller;

import com.hospital.common.Result;
import com.hospital.lis.dto.InspectionResultRequest;
import com.hospital.lis.service.LisInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/lis")
@RequiredArgsConstructor
public class LisController {

    private final LisInspectionService lisInspectionService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of("status", "UP", "service", "hospital-lis"));
    }

    @GetMapping("/queue")
    public Result<Map<String, Object>> queue(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(lisInspectionService.listQueue(status, page, pageSize));
    }

    @PostMapping("/requests/{id}/execute")
    public Result<Map<String, Object>> execute(@PathVariable Long id) {
        return Result.success(lisInspectionService.execute(id));
    }

    @GetMapping("/requests/{id}/result-detail")
    public Result<Map<String, Object>> resultDetail(@PathVariable Long id) {
        return Result.success(lisInspectionService.getResultDetail(id));
    }

    @PostMapping("/requests/{id}/ai-report")
    public Result<Map<String, Object>> aiReport(@PathVariable Long id) {
        return Result.success(lisInspectionService.generateAiReport(id));
    }

    @PostMapping("/requests/{id}/result")
    public Result<Map<String, Object>> saveResult(
            @PathVariable Long id,
            @RequestBody InspectionResultRequest request) {
        return Result.success(lisInspectionService.saveResult(id, request));
    }
}
