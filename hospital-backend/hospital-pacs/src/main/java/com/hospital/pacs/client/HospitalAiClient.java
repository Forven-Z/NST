package com.hospital.pacs.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.pacs.config.HospitalAiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HospitalAiClient {

    private final HospitalAiProperties hospitalAiProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public String submitInferenceJob(
            long studyId,
            long checkRequestId,
            String sourceBucket,
            String sourceObjectKeyPrefix,
            String resultPrefix
    ) {
        Map<String, Object> source = new HashMap<>();
        source.put("bucket", sourceBucket);
        source.put("objectKeyPrefix", sourceObjectKeyPrefix);

        Map<String, Object> body = new HashMap<>();
        body.put("studyId", studyId);
        body.put("checkRequestId", checkRequestId);
        body.put("source", source);
        body.put("resultPrefix", resultPrefix);
        body.put("callbackUrl", hospitalAiProperties.getCallbackUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = hospitalAiProperties.getBaseUrl().replaceAll("/$", "") + "/v1/inference/jobs";
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode jobId = root.path("jobId");
            if (jobId.isMissingNode() || jobId.isNull()) {
                jobId = root.path("data").path("jobId");
            }
            if (jobId.isMissingNode() || jobId.isNull()) {
                throw new IllegalStateException("hospital-ai 未返回 jobId");
            }
            return jobId.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析 hospital-ai 响应失败", e);
        }
    }
}
