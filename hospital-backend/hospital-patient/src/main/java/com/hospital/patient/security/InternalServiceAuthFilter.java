package com.hospital.patient.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.Result;
import com.hospital.common.constant.ErrorCode;
import com.hospital.patient.config.PatientProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
@RequiredArgsConstructor
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private final PatientProperties patientProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String caller = request.getHeader("X-Internal-Service");
        if (caller == null || !patientProperties.getInternal().getAllowedCallers().contains(caller)) {
            writeForbidden(response, "非法内部服务调用");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.FORBIDDEN, message));
    }
}
