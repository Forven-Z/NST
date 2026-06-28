package com.hospital.aibridge.controller;

import com.hospital.aibridge.dto.HeadCtImpressionRequest;
import com.hospital.aibridge.dto.LabAnalysisRequest;
import com.hospital.aibridge.service.CtReportAiService;
import com.hospital.aibridge.service.LabReportAiService;
import com.hospital.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/reports")
@RequiredArgsConstructor
public class AiReportController {

    private final CtReportAiService ctReportAiService;
    private final LabReportAiService labReportAiService;

    @PostMapping("/head-ct/impression")
    public Result<Map<String, Object>> headCtImpression(@RequestBody HeadCtImpressionRequest request) {
        return Result.success(ctReportAiService.generateHeadCtImpression(request));
    }

    @PostMapping("/lab/analysis")
    public Result<Map<String, Object>> labAnalysis(@RequestBody LabAnalysisRequest request) {
        return Result.success(labReportAiService.generateLabAnalysis(request));
    }
}
