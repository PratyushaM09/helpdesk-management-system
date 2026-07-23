package com.helpdesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.constant.ResponseMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter-chain equivalent of {@code ForbiddenException} — writes the same
 * {@code ErrorResponse} contract {@code GlobalExceptionHandler} produces for
 * every other 403 (SDR-009). Invoked two ways: by Spring Security's own
 * authorization layer (an authenticated-but-wrong-role request) and
 * directly by {@link CsrfValidationFilter} (a CSRF token mismatch) — so
 * there remains exactly one place a 403 body is constructed inside the
 * security filter chain, not two independently-written copies.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger auditLog = LoggerFactory.getLogger("com.helpdesk.security.audit");

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        auditLog.warn("Access denied: {} {} ({})", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
        SecurityErrorResponseWriter.write(response, objectMapper, HttpStatus.FORBIDDEN,
                "FORBIDDEN", ResponseMessages.ACCESS_DENIED, request.getRequestURI());
    }
}
