package com.hospital.management.controller;

import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/scheduling")
public class SchedulingAiStubController {

    @PostMapping("/ai-suggest")
    public Result<Void> aiSuggest() {
        return Result.fail(ErrorCode.AI_DISABLED, "AI 排班建议尚未接入，请使用手工编辑或手工替班");
    }

    @PostMapping("/{id}/ai-replace")
    public Result<Void> aiReplace(@PathVariable Long id) {
        return Result.fail(ErrorCode.AI_DISABLED, "AI 替班尚未接入，请使用「手工换人」");
    }
}
