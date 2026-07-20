package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * Placeholder for the future Authorization/RBAC milestone
 * (07-Security-Architecture.md §4, ADR-0004). Not thrown anywhere in this
 * codebase yet — no authorization checks exist. Reserved specifically for
 * an authenticated caller acting outside their permitted role/function
 * (BFLA); an unauthenticated-or-not-visible-to-you resource case uses
 * {@link ResourceNotFoundException} instead, per the "404, never 403 for
 * ownership" rule (07-Security-Architecture.md §4, 09-Security-Operations.md §18.2).
 */
public class ForbiddenException extends ApplicationException {

    private static final String ERROR_CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(ERROR_CODE, message, HttpStatus.FORBIDDEN);
    }
}
