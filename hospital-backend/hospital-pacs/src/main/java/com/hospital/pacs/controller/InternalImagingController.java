package com.hospital.pacs.controller;

import com.hospital.pacs.service.ImagingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/internal/imaging")
@RequiredArgsConstructor
public class InternalImagingController {

    private final ImagingService imagingService;

    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody Map<String, Object> payload) {
        imagingService.handleCallback(payload);
        return Map.of("accepted", true);
    }

    /**
     * 供 hospital-his 患者端代理拉取报告采图（内网，不经 Gateway JWT）。
     */
    @GetMapping("/report-preview/{checkRequestId}/{plane}")
    public void reportPreview(
            @PathVariable Long checkRequestId,
            @PathVariable String plane,
            HttpServletResponse response) throws Exception {
        try (InputStream in = imagingService.openReportSnapshotStream(checkRequestId, plane)) {
            response.setContentType("image/png");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + plane + ".png\"");
            StreamUtils.copy(in, response.getOutputStream());
        }
    }
}

