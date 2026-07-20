package com.helpdesk.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.helpdesk.constant.ResponseMessages;

import java.time.Instant;

/**
 * The single response envelope every future REST endpoint wraps a
 * successful (2xx) payload in — request/response consistency across
 * modules (04-API-Design.md §1) starts here.
 * <p>
 * {@code success} is always {@code true} for an {@code ApiResponse}
 * instance: an error is never represented as {@code ApiResponse(success =
 * false, ...)}. Every failure path returns {@link com.helpdesk.exception.ErrorResponse}
 * instead, via {@link com.helpdesk.exception.GlobalExceptionHandler} — one
 * envelope shape per outcome, never two ways to express the same thing.
 * The field is still explicit (not hardcoded away) because it keeps the
 * contract self-describing for a client parsing the body without also
 * needing to branch on the HTTP status code.
 *
 * @param data the payload; {@code null} for actions with no meaningful
 *             return value (e.g. a delete confirmation)
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(ResponseMessages.SUCCESS, data);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null, Instant.now());
    }
}
