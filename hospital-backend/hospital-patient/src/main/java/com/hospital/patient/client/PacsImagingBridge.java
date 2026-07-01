package com.hospital.patient.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PacsImagingBridge {

    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

    private final PacsImagingFeignClient pacsImagingFeignClient;
    private final ObjectMapper objectMapper;

    public byte[] fetchReportSnapshot(Long checkRequestId, String plane) {
        try {
            byte[] body = pacsImagingFeignClient.fetchReportSnapshot(checkRequestId, plane);
            return validatePng(body);
        } catch (FeignException ex) {
            throw parseFeignError(ex);
        }
    }

    private byte[] validatePng(byte[] body) {
        if (body == null || body.length < PNG_MAGIC.length) {
            throw tryParseJsonError(body);
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (body[i] != PNG_MAGIC[i]) {
                throw tryParseJsonError(body);
            }
        }
        return body;
    }

    private BusinessException tryParseJsonError(byte[] body) {
        if (body == null || body.length == 0) {
            return new BusinessException(ErrorCode.NOT_FOUND, "报告采图不存在");
        }
        try {
            Result<?> result = objectMapper.readValue(body, Result.class);
            int code = result.getCode() != null ? result.getCode() : ErrorCode.BAD_REQUEST;
            String message = result.getMessage() != null ? result.getMessage() : "获取报告采图失败";
            return new BusinessException(code, message);
        } catch (Exception ignored) {
            return new BusinessException(ErrorCode.BAD_REQUEST, "获取报告采图失败");
        }
    }

    private BusinessException parseFeignError(FeignException ex) {
        byte[] body = ex.responseBody().map(java.nio.ByteBuffer::array).orElse(null);
        if (body != null && body.length > 0) {
            BusinessException parsed = tryParseJsonError(body);
            if (parsed.getCode() != ErrorCode.BAD_REQUEST || !"获取报告采图失败".equals(parsed.getMessage())) {
                return parsed;
            }
        }
        return new BusinessException(ErrorCode.BAD_REQUEST, "PACS 服务不可用: " + ex.getMessage());
    }
}
