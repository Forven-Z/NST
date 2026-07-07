package com.hospital.common.auth;

import com.hospital.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenHelperTest {

    private static final String TEST_SECRET =
            "hospital-dev-secret-key-change-in-production-min-32-chars!!";

    private final SecretKey secretKey =
            Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    @Test
    void parse_validAccessToken() {
        String token = Jwts.builder()
                .subject("1")
                .claim(JwtClaims.TOKEN_KIND, JwtClaims.TOKEN_KIND_ACCESS)
                .claim(JwtClaims.ROLES, List.of("DOCTOR"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(secretKey)
                .compact();

        Claims claims = JwtTokenHelper.parse(token, TEST_SECRET);

        assertThat(JwtTokenHelper.isAccessToken(claims)).isTrue();
        assertThat(JwtTokenHelper.getRoles(claims)).containsExactly("DOCTOR");
    }

    @Test
    void isAccessToken_falseForRefreshToken() {
        String token = Jwts.builder()
                .subject("99")
                .claim(JwtClaims.TOKEN_KIND, JwtClaims.TOKEN_KIND_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(secretKey)
                .compact();
        Claims claims = JwtTokenHelper.parse(token, TEST_SECRET);

        assertThat(JwtTokenHelper.isAccessToken(claims)).isFalse();
    }

    @Test
    void getRoles_emptyWhenClaimMissing() {
        String token = Jwts.builder()
                .subject("99")
                .claim(JwtClaims.TOKEN_KIND, JwtClaims.TOKEN_KIND_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(secretKey)
                .compact();
        Claims claims = JwtTokenHelper.parse(token, TEST_SECRET);

        assertThat(JwtTokenHelper.getRoles(claims)).isEmpty();
    }

    @Test
    void parse_rejectsInvalidToken() {
        assertThatThrownBy(() -> JwtTokenHelper.parse("bad.token", TEST_SECRET))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Token 无效");
    }
}
