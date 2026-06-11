package com.hospital.pacs.service;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ImagingCallbackRegistry {

    private final ConcurrentHashMap<Long, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();

    public void register(Long checkRequestId) {
        pending.compute(checkRequestId, (id, existing) -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            return new CompletableFuture<>();
        });
    }

    public Map<String, Object> await(Long checkRequestId, long timeoutSeconds) {
        CompletableFuture<Map<String, Object>> future = pending.get(checkRequestId);
        if (future == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未注册影像回调等待");
        }
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(checkRequestId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 影像分析超时，请稍后重试");
        } catch (Exception e) {
            pending.remove(checkRequestId);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 影像分析失败: " + cause.getMessage());
        } finally {
            if (future.isDone()) {
                pending.remove(checkRequestId);
            }
        }
    }

    public void complete(Long checkRequestId, Map<String, Object> payload) {
        CompletableFuture<Map<String, Object>> future = pending.get(checkRequestId);
        if (future != null) {
            future.complete(payload);
        }
    }

    public void fail(Long checkRequestId, String message) {
        CompletableFuture<Map<String, Object>> future = pending.get(checkRequestId);
        if (future != null) {
            future.completeExceptionally(new BusinessException(ErrorCode.BAD_REQUEST, message));
        }
    }
}
