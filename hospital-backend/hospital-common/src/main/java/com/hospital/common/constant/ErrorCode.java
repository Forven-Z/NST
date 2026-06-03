package com.hospital.common.constant;

/**
 * 常用业务错误码，与 HTTP 状态解耦，便于前端统一处理。
 */
public final class ErrorCode {

    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int BAD_REQUEST = 400;
    public static final int AI_DISABLED = 50301;

    private ErrorCode() {
    }
}
