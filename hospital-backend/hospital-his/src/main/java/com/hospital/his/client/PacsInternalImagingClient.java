package com.hospital.his.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class PacsInternalImagingClient {

    static final String SERVICE_BASE = "http://hospital-pacs/internal/imaging/report-preview/";

    private final RestTemplate loadBalancedRestTemplate;
    private final ObjectMapper objectMapper;

    public byte[] fetchReportSnapshot(Long checkRequestId, String plane) {
        String url = SERVICE_BASE + checkRequestId + "/" + plane;
        ResponseEntity<byte[]> response = loadBalancedRestTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                byte[].class
        );
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报告采图不存在");
        }
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType != null && MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw parseBusinessError(body);
        }
        return body;
    }

    private BusinessException parseBusinessError(byte[] body) {
        try {
            Result<?> result = objectMapper.readValue(body, Result.class);
            int code = result.getCode() != null ? result.getCode() : ErrorCode.BAD_REQUEST;
            String message = result.getMessage() != null ? result.getMessage() : "获取报告采图失败";
            if (code == ErrorCode.NOT_FOUND) {
                return new BusinessException(ErrorCode.NOT_FOUND, message);
            }
            if (code == ErrorCode.FORBIDDEN) {
                return new BusinessException(ErrorCode.FORBIDDEN, message);
            }
            return new BusinessException(code, message);
        } catch (Exception ex) {
            return new BusinessException(ErrorCode.BAD_REQUEST, "获取报告采图失败");
        }
    }
}
