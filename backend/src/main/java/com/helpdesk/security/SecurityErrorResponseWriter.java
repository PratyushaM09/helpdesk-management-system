package com.helpdesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * The one place a {@link ErrorResponse} body is written from inside the
 * security filter chain — shared by {@link RestAuthenticationEntryPoint}
 * (401) and {@link RestAccessDeniedHandler} (403), which are otherwise
 * unrelated classes that would each independently reimplement the exact
 * same "build the envelope, set status/content-type, serialize" sequence.
 * Both cases share one property: they are rejected before
 * {@code DispatcherServlet} runs, so {@code GlobalExceptionHandler}
 * (a {@code @ControllerAdvice}) never sees them — this is the equivalent
 * writer for that boundary, producing byte-for-byte the same envelope
 * shape (SDR-009).
 */
final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                       String errorCode, String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse errorResponse = ErrorResponse.of(status, errorCode, message, path);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
