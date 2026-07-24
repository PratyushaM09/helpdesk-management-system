package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * An unauthenticated or invalid-credential outcome the Service layer itself
 * detects (07-Security-Architecture.md §3, ADR-0003) — invalid email/
 * password at login, or a missing/invalid/expired/reused refresh token
 * (thrown by {@code AuthenticationServiceImpl}/{@code AuthenticationController}).
 * Distinct from an unauthenticated request to an already-protected route,
 * which never reaches application code at all: that's rejected by
 * {@code RestAuthenticationEntryPoint} inside the security filter chain,
 * using the same {@link ErrorResponse} shape this exception produces via
 * {@link GlobalExceptionHandler}.
 */
public class UnauthorizedException extends ApplicationException {

    private static final String ERROR_CODE = "UNAUTHORIZED";

    public UnauthorizedException(String message) {
        super(ERROR_CODE, message, HttpStatus.UNAUTHORIZED);
    }
}
