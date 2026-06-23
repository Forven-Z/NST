package com.hospital.management.client;

import com.hospital.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = AiSchedulingFeignClient.SERVICE_NAME)
public interface AiSchedulingFeignClient {

    String SERVICE_NAME = "hospital-ai-bridge";

    @PostMapping("/api/v1/ai/scheduling/suggest")
    Result<Map<String, Object>> suggest(@RequestBody Map<String, Object> request);
}
