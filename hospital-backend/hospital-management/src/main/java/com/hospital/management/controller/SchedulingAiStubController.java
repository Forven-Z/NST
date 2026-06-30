package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.management.dto.SchedulingAiSuggestRequest;
import com.hospital.management.service.SchedulingAiSuggestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/scheduling")
@RequiredArgsConstructor
public class SchedulingAiStubController {

    private final SchedulingAiSuggestService schedulingAiSuggestService;

    @PostMapping("/ai-suggest")
    public Result<Map<String, Object>> aiSuggest(@RequestBody(required = false) SchedulingAiSuggestRequest request) {
        Long deptId = request == null ? null : request.getDeptId();
        return Result.success(schedulingAiSuggestService.suggest(
                deptId,
                request == null ? null : request.getWeekStart(),
                request == null ? null : request.getMode()));
    }

    @PostMapping("/{id}/ai-replace")
    public Result<Map<String, Object>> aiReplace(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(schedulingAiSuggestService.replace(id, request));
    }
}
