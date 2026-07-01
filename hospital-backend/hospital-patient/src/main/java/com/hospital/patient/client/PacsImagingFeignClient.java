package com.hospital.patient.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = PacsImagingFeignClient.SERVICE_NAME)
public interface PacsImagingFeignClient {

    String SERVICE_NAME = "hospital-pacs";

    @GetMapping("/internal/imaging/report-preview/{checkRequestId}/{plane}")
    byte[] fetchReportSnapshot(@PathVariable("checkRequestId") Long checkRequestId,
                               @PathVariable("plane") String plane);
}
