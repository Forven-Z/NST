package com.hospital.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.Result;
import com.hospital.common.auth.JwtClaims;
import com.hospital.common.auth.JwtTokenHelper;
import com.hospital.common.auth.UserType;
import com.hospital.common.constant.ErrorCode;
import com.hospital.common.exception.BusinessException;
import com.hospital.gateway.config.HospitalGatewayAuthProperties;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 统一 JWT 校验：白名单放行，其余请求必须携带有效 accessToken，并按用户类型限制 API 前缀。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String API_PREFIX = "/api/v1/";

    private final HospitalGatewayAuthProperties gatewayAuthProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (!path.startsWith(API_PREFIX)) {
            return chain.filter(exchange);
        }

        if (isWhitelisted(path, request.getMethod())) {
            return chain.filter(exchange);
        }

        try {
            Claims claims = resolveClaims(request);
            assertPathAllowed(path, claims);
            return chain.filter(exchange);
        } catch (BusinessException ex) {
            return writeError(exchange, ex.getCode(), ex.getMessage());
        }
    }

    private Claims resolveClaims(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(JwtClaims.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(JwtClaims.BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 Authorization Bearer Token");
        }
        String token = authorization.substring(JwtClaims.BEARER_PREFIX.length());
        Claims claims = JwtTokenHelper.parse(token, gatewayAuthProperties.getJwt().getSecret());
        if (!JwtTokenHelper.isAccessToken(claims)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请使用 accessToken 访问");
        }
        return claims;
    }

    private void assertPathAllowed(String path, Claims claims) {
        UserType userType = UserType.valueOf(claims.get(JwtClaims.TYPE, String.class));

        if (path.startsWith("/api/v1/patient/") && userType != UserType.PATIENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要患者身份");
        }
        if (path.startsWith("/api/v1/doctor/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/lis/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/pacs/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/disposal/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/pharmacy/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/registrar/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/staff/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/ai/triage/") && userType != UserType.PATIENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要患者身份");
        }
        if (path.startsWith("/api/v1/ai/assistant/") && userType != UserType.STAFF && userType != UserType.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要医护身份");
        }
        if (path.startsWith("/api/v1/admin/")) {
            List<String> roles = JwtTokenHelper.getRoles(claims);
            if (userType != UserType.ADMIN && !roles.contains("ADMIN")) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员身份");
            }
        }
    }

    private boolean isWhitelisted(String path, HttpMethod method) {
        String httpMethod = method != null ? method.name() : null;
        for (HospitalGatewayAuthProperties.WhitelistRule rule : gatewayAuthProperties.getWhitelist()) {
            if (!pathMatcher.match(rule.getPath(), path)) {
                continue;
            }
            if (rule.getMethods() == null || rule.getMethods().isEmpty()) {
                return true;
            }
            if (httpMethod != null && rule.getMethods().stream().anyMatch(httpMethod::equalsIgnoreCase)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> body = Result.fail(code, message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\",\"success\":false}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
