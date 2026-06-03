package com.hospital.common.auth;

/**
 * JWT 自定义 Claim 字段名 — auth 签发、Gateway 校验、业务服务解析时共用，避免魔法字符串散落各处。
 */
public final class JwtClaims {

    public static final String TYPE = "type";
    public static final String TOKEN_KIND = "tokenKind";
    public static final String USER_ID = "userId";
    public static final String EMPLOYEE_ID = "employeeId";
    public static final String PATIENT_ID = "patientId";
    public static final String MEDICAL_RECORD_NO = "medicalRecordNo";
    public static final String ROLES = "roles";

    public static final String TOKEN_KIND_ACCESS = "ACCESS";
    public static final String TOKEN_KIND_REFRESH = "REFRESH";

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private JwtClaims() {
    }
}
