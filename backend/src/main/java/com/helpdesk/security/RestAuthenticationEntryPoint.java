package com.helpdesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.constant.ResponseMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter-chain equivalent of {@code UnauthorizedException} — writes the same
 * {@code ErrorResponse} contract {@code GlobalExceptionHandler} produces for
 * every other 401 (SDR-009), for the one case it can never see: an
 * unauthenticated request to a protected route, rejected by Spring
 * Security's authorization layer before {@code DispatcherServlet} (and
 * therefore any {@code @ControllerAdvice}) ever runs.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger auditLog = LoggerFactory.getLogger("com.helpdesk.security.audit");

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        auditLog.warn("Unauthenticated request rejected: {} {}", request.getMethod(), request.getRequestURI());
        SecurityErrorResponseWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", ResponseMessages.AUTHENTICATION_REQUIRED, request.getRequestURI());
    }
}
