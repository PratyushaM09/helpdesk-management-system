package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is well-formed but conflicts with current state — a
 * duplicate-uniqueness violation, an illegal state transition, or (per
 * ADR-0010) an optimistic-locking version conflict. Future modules throw
 * this rather than letting a raw database constraint violation or
 * {@code OptimisticLockException} surface past the Service layer.
 */
public class ConflictException extends ApplicationException {

    private static final String ERROR_CODE = "CONFLICT";

    public ConflictException(String message) {
        super(ERROR_CODE, message, HttpStatus.CONFLICT);
    }
}
