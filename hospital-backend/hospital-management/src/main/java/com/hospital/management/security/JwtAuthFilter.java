package com.hospital.management.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.Result;
import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.JwtTokenHelper;
import com.hospital.common.auth.UserType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.management.config.ManagementProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final ManagementProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("/api/v1/admin/health".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String authorization = request.getHeader(JwtClaims.AUTHORIZATION_HEADER);
            if (authorization == null || !authorization.startsWith(JwtClaims.BEARER_PREFIX)) {
                writeError(response, ErrorCode.UNAUTHORIZED, "缺少 Authorization Bearer Token");
                return;
            }

            Claims claims = JwtTokenHelper.parse(
                    authorization.substring(JwtClaims.BEARER_PREFIX.length()),
                    properties.getAuth().getJwt().getSecret());
            if (!JwtTokenHelper.isAccessToken(claims)) {
                writeError(response, ErrorCode.UNAUTHORIZED, "请使用 accessToken 访问");
                return;
            }

            UserType userType = UserType.valueOf(claims.get(JwtClaims.TYPE, String.class));
            List<String> roles = JwtTokenHelper.getRoles(claims);
            if (userType != UserType.ADMIN && !roles.contains("ADMIN")) {
                writeError(response, ErrorCode.FORBIDDEN, "需要管理员身份");
                return;
            }

            chain.doFilter(request, response);
        } catch (Exception ex) {
            writeError(response, ErrorCode.UNAUTHORIZED, "Token 无效");
        }
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
