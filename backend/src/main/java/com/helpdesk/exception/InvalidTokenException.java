package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * A presented password-reset or email-verification token doesn't resolve to
 * something redeemable — missing, expired, or already used all throw this,
 * with the same message, so the caller can never distinguish which
 * condition occurred (Milestone 4 design, Change 2). Same status/shape as
 * {@link UnauthorizedException}, the precedent already set for an invalid
 * opaque bearer token ({@code AuthenticationServiceImpl}'s "Invalid refresh
 * token." on an unknown/expired/revoked/already-rotated refresh token) — a
 * reset/verification token is the same kind of thing, just issued for a
 * different purpose.
 */
public class InvalidTokenException extends ApplicationException {

    private static final String ERROR_CODE = "INVALID_TOKEN";

    public InvalidTokenException(String message) {
        super(ERROR_CODE, message, HttpStatus.UNAUTHORIZED);
    }
}
