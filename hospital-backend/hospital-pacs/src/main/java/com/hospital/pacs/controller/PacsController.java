package com.hospital.pacs.controller;

import com.hospital.common.Result;
import com.hospital.pacs.dto.CheckResultRequest;
import com.hospital.pacs.service.PacsCheckService;
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
@RequestMapping("/api/v1/pacs")
@RequiredArgsConstructor
public class PacsController {

    private final PacsCheckService pacsCheckService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of("status", "UP", "service", "hospital-pacs"));
    }

    @GetMapping("/queue")
    public Result<Map<String, Object>> queue(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(pacsCheckService.listQueue(status, page, pageSize));
    }

    @PostMapping("/requests/{id}/execute")
    public Result<Map<String, Object>> execute(@PathVariable Long id) {
        return Result.success(pacsCheckService.execute(id));
    }

    @PostMapping("/requests/{id}/result")
    public Result<Map<String, Object>> saveResult(
            @PathVariable Long id,
            @Valid @RequestBody CheckResultRequest request) {
        return Result.success(pacsCheckService.saveResult(id, request));
    }
}
