package com.hospital.pacs.controller;

import com.hospital.pacs.service.ImagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
