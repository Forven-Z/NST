package com.hospital.management.security;

public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> CTX = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthContext context) {
        CTX.set(context);
    }

    public static AuthContext require() {
        AuthContext ctx = CTX.get();
        if (ctx == null) {
            throw new IllegalStateException("AuthContext not set");
        }
        return ctx;
    }

    public static void clear() {
        CTX.remove();
    }
}
