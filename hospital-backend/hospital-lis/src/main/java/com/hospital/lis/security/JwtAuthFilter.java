package com.hospital.lis.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.Result;
import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.JwtTokenHelper;
import com.hospital.common.auth.UserType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.lis.config.LisProperties;
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

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final LisProperties lisProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/lis/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("/api/v1/lis/health".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String authorization = request.getHeader(JwtClaims.AUTHORIZATION_HEADER);
            if (authorization == null || !authorization.startsWith(JwtClaims.BEARER_PREFIX)) {
                writeError(response, ErrorCode.UNAUTHORIZED, "缺少 Authorization Bearer Token");
                return;
            }

            String token = authorization.substring(JwtClaims.BEARER_PREFIX.length());
            Claims claims = JwtTokenHelper.parse(token, lisProperties.getAuth().getJwt().getSecret());
            if (!JwtTokenHelper.isAccessToken(claims)) {
                writeError(response, ErrorCode.UNAUTHORIZED, "请使用 accessToken 访问");
                return;
            }

            UserType userType = UserType.valueOf(claims.get(JwtClaims.TYPE, String.class));
            AuthContext context = AuthContext.builder()
                    .userType(userType)
                    .userId(claims.get(JwtClaims.USER_ID, Long.class))
                    .employeeId(claims.get(JwtClaims.EMPLOYEE_ID, Long.class))
                    .roles(JwtTokenHelper.getRoles(claims))
                    .build();

            if (!context.isStaff()) {
                writeError(response, ErrorCode.FORBIDDEN, "需要医护身份");
                return;
            }

            AuthContextHolder.set(context);
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
