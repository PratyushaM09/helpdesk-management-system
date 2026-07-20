package com.helpdesk.exception;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

/**
 * Abstract base of every custom exception in this codebase. Carries a
 * stable, client-safe {@code errorCode} and the HTTP status it maps to, so
 * {@link GlobalExceptionHandler} needs exactly one handler method to
 * translate any subclass into a response — a new exception type never
 * requires a new handler method, only a new subclass.
 * <p>
 * <b>Caution for every future caller:</b> the {@code message} passed to a
 * subclass constructor is returned to the client as-is (see
 * {@link GlobalExceptionHandler}). Never build it from raw internal detail
 * (a SQL fragment, a file path, another exception's message) or from
 * anything on the "never log" list (09-Security-Operations.md §16.2) —
 * write it exactly as you'd want a user to read it.
 */
public abstract class ApplicationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected ApplicationException(@NonNull String errorCode, String message, @NonNull HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    @NonNull
    public String getErrorCode() {
        return errorCode;
    }

    @NonNull
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
