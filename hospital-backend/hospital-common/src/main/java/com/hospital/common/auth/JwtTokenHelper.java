package com.hospital.common.auth;

import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * JWT 解析工具 — auth 签发、his/gateway 校验共用同一套 Claim 约定。
 */
public final class JwtTokenHelper {

    private JwtTokenHelper() {
    }

    public static Claims parse(String token, String secret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token 无效或已过期");
        }
    }

    public static boolean isAccessToken(Claims claims) {
        return JwtClaims.TOKEN_KIND_ACCESS.equals(claims.get(JwtClaims.TOKEN_KIND, String.class));
    }

    @SuppressWarnings("unchecked")
    public static List<String> getRoles(Claims claims) {
        Object roles = claims.get(JwtClaims.ROLES);
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }
}
