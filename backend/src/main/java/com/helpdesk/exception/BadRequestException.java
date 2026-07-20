package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * The request itself is malformed or violates a rule that isn't specific
 * to any one resource (e.g. a business-rule check a future Service throws
 * before persistence validation would catch it). Bean Validation failures
 * (structural DTO validation) are a distinct, framework-raised path — see
 * GlobalExceptionHandler's note on why that isn't wired up yet.
 */
public class BadRequestException extends ApplicationException {

    private static final String ERROR_CODE = "BAD_REQUEST";

    public BadRequestException(String message) {
        super(ERROR_CODE, message, HttpStatus.BAD_REQUEST);
    }
}
