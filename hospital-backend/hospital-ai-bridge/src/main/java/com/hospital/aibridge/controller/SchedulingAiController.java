package com.hospital.aibridge.controller;

import com.hospital.aibridge.service.SchedulingAiService;
import com.hospital.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/scheduling")
@RequiredArgsConstructor
public class SchedulingAiController {

    private final SchedulingAiService schedulingAiService;

    @PostMapping("/suggest")
    public Result<Map<String, Object>> suggest(@RequestBody Map<String, Object> request) {
        return Result.success(schedulingAiService.suggest(request));
    }
}
