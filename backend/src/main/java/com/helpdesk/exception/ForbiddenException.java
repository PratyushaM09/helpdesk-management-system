package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * Reserved for a Service-layer, business-rule authorization denial
 * (07-Security-Architecture.md §4, ADR-0004) — e.g. a future ownership
 * check ("you may act on your own tickets, not anyone else's") that isn't
 * expressible as the static role check {@code @PreAuthorize} performs.
 * Not thrown anywhere in this codebase yet: every 403 today is a role
 * check, and those are handled by {@code @PreAuthorize} plus
 * {@code GlobalExceptionHandler}'s {@code AccessDeniedException} handler,
 * not this class. Kept in the exception hierarchy for the first
 * business-rule case that needs it, so no {@link GlobalExceptionHandler}
 * change is required when that day comes. An unauthenticated-or-not-
 * visible-to-you resource case uses {@link ResourceNotFoundException}
 * instead, per the "404, never 403 for ownership" rule
 * (07-Security-Architecture.md §4, 09-Security-Operations.md §18.2).
 */
public class ForbiddenException extends ApplicationException {

    private static final String ERROR_CODE = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(ERROR_CODE, message, HttpStatus.FORBIDDEN);
    }
}
