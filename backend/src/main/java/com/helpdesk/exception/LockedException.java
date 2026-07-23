package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * An account is locked out (SDR-005: 5 consecutive failed login attempts,
 * 15-minute sliding window, 07-Security-Architecture.md §3.9). Distinct from
 * {@link UnauthorizedException} — a locked account never reaches the
 * password comparison at all, so this is a different, more specific outcome
 * than "wrong credentials."
 */
public class LockedException extends ApplicationException {

    private static final String ERROR_CODE = "ACCOUNT_LOCKED";

    public LockedException(String message) {
        super(ERROR_CODE, message, HttpStatus.LOCKED);
    }
}
