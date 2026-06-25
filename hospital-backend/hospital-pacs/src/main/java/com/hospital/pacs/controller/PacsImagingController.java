package com.hospital.pacs.controller;

import com.hospital.common.Result;
import com.hospital.pacs.service.ImagingService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pacs")
@RequiredArgsConstructor
public class PacsImagingController {

    private final ImagingService imagingService;

    @PostMapping(value = "/imaging/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadImaging(
            @RequestParam Long checkRequestId,
            @RequestParam("files") MultipartFile[] files) {
        return Result.success(imagingService.uploadImaging(checkRequestId, files));
    }

    @GetMapping("/imaging-studies")
    public Result<Map<String, Object>> imagingStudies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String medicalRecordNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(imagingService.listImagingStudies(
                status, patientId, medicalRecordNo, page, pageSize));
    }

    @PostMapping("/requests/{id}/ai-report")
    public Result<Map<String, Object>> aiReport(@PathVariable Long id) {
        return Result.success(imagingService.generateAiReport(id));
    }

    @PostMapping("/requests/{id}/report-snapshots")
    public Result<Map<String, Object>> reportSnapshots(
            @PathVariable Long id,
            @RequestParam(required = false) MultipartFile axial,
            @RequestParam(required = false) MultipartFile coronal,
            @RequestParam(required = false) MultipartFile sagittal,
            @RequestParam(required = false) String meta) {
        return Result.success(imagingService.saveReportSnapshots(id, axial, coronal, sagittal, meta));
    }

    @GetMapping("/imaging/report-preview/{checkRequestId}/{plane}")
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

    @GetMapping("/requests/{id}/imaging-preview")
    public Result<Map<String, Object>> imagingPreview(@PathVariable Long id) {
        return Result.success(imagingService.getImagingPreview(id));
    }

    @GetMapping("/imaging/preview/{checkRequestId}/{kind}")
    public void previewFile(
            @PathVariable Long checkRequestId,
            @PathVariable String kind,
            HttpServletResponse response) throws Exception {
        try (InputStream in = imagingService.openPreviewStream(checkRequestId, kind)) {
            response.setContentType("application/gzip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + kind + ".nii.gz\"");
            StreamUtils.copy(in, response.getOutputStream());
        }
    }
}
