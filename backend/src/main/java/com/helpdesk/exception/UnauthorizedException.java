package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * Placeholder for the future Authentication milestone
 * (07-Security-Architecture.md §3, ADR-0003). Not thrown anywhere in this
 * codebase yet — no authentication mechanism exists. Included now so the
 * exception hierarchy and {@link GlobalExceptionHandler} are already
 * complete and require no changes when JWT authentication is introduced;
 * only the code that verifies credentials needs to start throwing it.
 */
public class UnauthorizedException extends ApplicationException {

    private static final String ERROR_CODE = "UNAUTHORIZED";

    public UnauthorizedException(String message) {
        super(ERROR_CODE, message, HttpStatus.UNAUTHORIZED);
    }
}
