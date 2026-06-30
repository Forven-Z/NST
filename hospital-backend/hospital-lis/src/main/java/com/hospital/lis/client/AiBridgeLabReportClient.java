package com.hospital.lis.client;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.lis.config.AiBridgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiBridgeLabReportClient {

    private final AiBridgeProperties aiBridgeProperties;
    private final RestTemplate restTemplate;

    public String generateLabAnalysis(Map<String, Object> context, List<Map<String, Object>> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemName", context.get("itemName"));
        payload.put("patientGender", context.get("gender"));
        payload.put("patientAge", context.get("age"));
        payload.put("clinicalDiagnosis", context.get("clinicalDiagnosis"));
        payload.put("items", items);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = aiBridgeProperties.getBaseUrl().replaceAll("/+$", "")
                + "/api/v1/ai/reports/lab/analysis";
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(payload, headers),
                    Map.class);
            return extractAiReportText(response.getBody());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "调用 AI 检验报告生成服务失败: " + ex.getMessage());
        }
    }

    private String extractAiReportText(Map<?, ?> body) {
        if (body == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 检验报告生成服务未返回响应");
        }
        if (Boolean.FALSE.equals(body.get("success"))) {
            Object code = body.get("code");
            int errorCode = code instanceof Number number ? number.intValue() : ErrorCode.BAD_REQUEST;
            Object message = body.containsKey("message") ? body.get("message") : "AI 检验报告生成失败";
            throw new BusinessException(errorCode, String.valueOf(message));
        }
        Object data = body.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 检验报告生成服务响应格式无效");
        }
        Object text = dataMap.get("aiReportText");
        String aiReportText = text == null ? "" : String.valueOf(text).trim();
        if (aiReportText.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 检验报告生成服务返回空诊断分析");
        }
        return aiReportText;
    }
}
