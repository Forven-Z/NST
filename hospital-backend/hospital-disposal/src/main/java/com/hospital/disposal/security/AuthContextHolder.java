package com.hospital.disposal.security;

public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> CONTEXT = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthContext context) {
        CONTEXT.set(context);
    }

    public static AuthContext require() {
        AuthContext context = CONTEXT.get();
        if (context == null) {
            throw new com.hospital.common.exception.BusinessException(
                    com.hospital.common.constant.ErrorCode.UNAUTHORIZED, "未登录或 Token 无效");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
