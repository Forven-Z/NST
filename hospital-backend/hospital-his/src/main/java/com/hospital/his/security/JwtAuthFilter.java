package com.hospital.his.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.Result;
import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.JwtTokenHelper;
import com.hospital.common.auth.UserType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.his.config.HisProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final List<String> WHITELIST = List.of(
            "/api/v1/patient/auth/login",
            "/api/v1/patient/auth/wechat"
    );

    private final HisProperties hisProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/patient/")
                && !path.startsWith("/api/v1/doctor/")
                && !path.startsWith("/api/v1/pharmacy/")
                && !path.startsWith("/api/v1/registrar/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String authorization = request.getHeader(JwtClaims.AUTHORIZATION_HEADER);
            if (authorization == null || !authorization.startsWith(JwtClaims.BEARER_PREFIX)) {
                writeUnauthorized(response, "缺少 Authorization Bearer Token");
                return;
            }

            String token = authorization.substring(JwtClaims.BEARER_PREFIX.length());
            Claims claims = JwtTokenHelper.parse(token, hisProperties.getAuth().getJwt().getSecret());
            if (!JwtTokenHelper.isAccessToken(claims)) {
                writeUnauthorized(response, "请使用 accessToken 访问");
                return;
            }

            UserType userType = UserType.valueOf(claims.get(JwtClaims.TYPE, String.class));
            AuthContext context = AuthContext.builder()
                    .userType(userType)
                    .userId(claims.get(JwtClaims.USER_ID, Long.class))
                    .employeeId(claims.get(JwtClaims.EMPLOYEE_ID, Long.class))
                    .patientId(claims.get(JwtClaims.PATIENT_ID, Long.class))
                    .roles(JwtTokenHelper.getRoles(claims))
                    .build();

            if (path.startsWith("/api/v1/patient/") && !context.isPatient()) {
                writeForbidden(response, "需要患者身份");
                return;
            }
            if (path.startsWith("/api/v1/doctor/") && !context.isStaff()) {
                writeForbidden(response, "需要医护身份");
                return;
            }
            if (path.startsWith("/api/v1/pharmacy/") && !context.isStaff()) {
                writeForbidden(response, "需要医护身份");
                return;
            }
            if (path.startsWith("/api/v1/registrar/") && !context.isStaff()) {
                writeForbidden(response, "需要医护身份");
                return;
            }

            AuthContextHolder.set(context);
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeError(response, ErrorCode.UNAUTHORIZED, message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        writeError(response, ErrorCode.FORBIDDEN, message);
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
    }
}
