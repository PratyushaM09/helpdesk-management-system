package com.helpdesk.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * The single response envelope every failure returns, produced only by
 * {@link GlobalExceptionHandler} — never constructed ad hoc in a
 * controller (02-Architecture.md §12, 09-Security-Operations.md §18.2).
 * <p>
 * Deliberately excludes a stack trace, SQL fragment, file path, or any
 * other internal detail — full detail is logged server-side only
 * (see GlobalExceptionHandler); this record is what's safe to serialize
 * to an untrusted client.
 * <p>
 * {@code errorCode} is a stable, machine-readable category (e.g.
 * {@code "RESOURCE_NOT_FOUND"}) a client can branch on without parsing
 * {@code message} — required by the already-accepted error contract
 * (09-Security-Operations.md §18.2, SDR-009) and populated from each
 * {@link ApplicationException} subclass's own error code.
 * <p>
 * {@code validationErrors} is populated only for a validation-shaped
 * failure (Milestone 4) and omitted from the JSON body entirely
 * ({@link JsonInclude.Include#NON_NULL}) for every other error — the
 * common case (a 404, a 409, ...) stays exactly as compact as it was
 * before this field existed.
 * <p>
 * A {@code traceId} (log-correlation) is still intentionally not
 * included — there is no correlation-id infrastructure in this milestone
 * either. It remains the extension point for the milestone that
 * introduces request logging correlation.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ValidationError> validationErrors
) {

    public static ErrorResponse of(HttpStatus status, String errorCode, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), errorCode, message, path, null);
    }

    public static ErrorResponse ofValidation(HttpStatus status, String errorCode, String message, String path,
                                              List<ValidationError> validationErrors) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), errorCode, message, path, validationErrors);
    }
}
