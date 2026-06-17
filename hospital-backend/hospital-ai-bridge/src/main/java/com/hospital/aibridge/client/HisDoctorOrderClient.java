package com.hospital.aibridge.client;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HisDoctorOrderClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String hisBaseUrl;

    public HisDoctorOrderClient(@Value("${hospital.his.base-url:http://127.0.0.1:9102}") String hisBaseUrl) {
        this.hisBaseUrl = hisBaseUrl.replaceAll("/+$", "");
    }

    public Map<String, Object> submitDraft(
            Long registerId,
            String draftType,
            List<Map<String, Object>> items,
            String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "确认 AI 草稿需要转发医生 Authorization Token");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 草稿明细不能为空");
        }
        return switch (draftType) {
            case "CHECK" -> submitClinicalOrders(registerId, items, authorization, "/api/v1/doctor/check-requests");
            case "INSPECTION" -> submitClinicalOrders(registerId, items, authorization, "/api/v1/doctor/inspection-requests");
            case "DISPOSAL" -> submitClinicalOrders(registerId, items, authorization, "/api/v1/doctor/disposal-requests");
            case "PRESCRIPTION" -> submitPrescription(registerId, items, authorization);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的 AI 草稿类型: " + draftType);
        };
    }

    private Map<String, Object> submitClinicalOrders(
            Long registerId,
            List<Map<String, Object>> items,
            String authorization,
            String path) {
        List<Map<String, Object>> results = items.stream()
                .map(item -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("registerId", registerId);
                    payload.put("medicalTechnologyId", item.get("medicalTechnologyId"));
                    payload.put("purpose", item.get("purpose"));
                    payload.put("bodyPart", item.get("bodyPart"));
                    payload.put("remark", item.get("remark"));
                    return post(path, payload, authorization);
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("submitted", true);
        response.put("results", results);
        return response;
    }

    private Map<String, Object> submitPrescription(
            Long registerId,
            List<Map<String, Object>> items,
            String authorization) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("registerId", registerId);
        payload.put("remark", "AI 辅助处方，医生确认后提交");
        payload.put("items", items.stream().map(this::prescriptionItem).toList());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("submitted", true);
        response.put("result", post("/api/v1/doctor/prescriptions", payload, authorization));
        return response;
    }

    private Map<String, Object> prescriptionItem(Map<String, Object> item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("drugId", item.get("drugId"));
        payload.put("quantity", item.get("quantity"));
        payload.put("usageMethod", item.get("usageMethod"));
        payload.put("dosage", item.get("dosage"));
        payload.put("frequency", item.get("frequency"));
        payload.put("days", item.get("days"));
        payload.put("entrust", item.get("entrust"));
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> payload, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    hisBaseUrl + path,
                    new HttpEntity<>(payload, headers),
                    Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "HIS 未返回响应");
            }
            if (Boolean.FALSE.equals(body.get("success"))) {
                Object code = body.get("code");
                int errorCode = code instanceof Number number ? number.intValue() : ErrorCode.BAD_REQUEST;
                throw new BusinessException(errorCode, String.valueOf(body.getOrDefault("message", "HIS 开单失败")));
            }
            Object data = body.get("data");
            if (data instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", data);
            return result;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "调用 HIS 开单接口失败: " + ex.getMessage());
        }
    }
}
