package com.hospital.disposal.controller;

import com.hospital.common.Result;
import com.hospital.disposal.dto.DisposalResultRequest;
import com.hospital.disposal.service.DisposalExecuteService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/disposal")
@RequiredArgsConstructor
public class DisposalController {

    private final DisposalExecuteService disposalExecuteService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of("status", "UP", "service", "hospital-disposal"));
    }

    @GetMapping("/queue")
    public Result<Map<String, Object>> queue(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(disposalExecuteService.listQueue(status, page, pageSize));
    }

    @PostMapping("/requests/{id}/execute")
    public Result<Map<String, Object>> execute(@PathVariable Long id) {
        return Result.success(disposalExecuteService.execute(id));
    }

    @PostMapping("/requests/{id}/result")
    public Result<Map<String, Object>> saveResult(
            @PathVariable Long id,
            @Valid @RequestBody DisposalResultRequest request) {
        return Result.success(disposalExecuteService.saveResult(id, request));
    }

    @GetMapping("/requests/{id}/result-detail")
    public Result<Map<String, Object>> resultDetail(@PathVariable Long id) {
        return Result.success(disposalExecuteService.getResultDetail(id));
    }

    @PostMapping("/requests/{id}/ai-report")
    public Result<Map<String, Object>> generateAiReport(@PathVariable Long id) {
        return Result.success(disposalExecuteService.generateAiReport(id));
    }
}
