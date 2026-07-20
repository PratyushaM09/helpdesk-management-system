package com.helpdesk.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One field-level failure inside {@link ErrorResponse#validationErrors()}.
 * Populated only by {@link GlobalExceptionHandler}'s validation-related
 * handlers, from a Bean Validation {@code FieldError}/{@code ConstraintViolation}.
 *
 * @param field         the rejected field/parameter name
 * @param rejectedValue the value that failed validation, or {@code null}
 *                      if it was never present, sensitive (see
 *                      {@code com.helpdesk.util.RejectedValueSanitizer}), or
 *                      not a simple enough type to safely echo back
 * @param message       the resolved, client-safe constraint message
 */
public record ValidationError(
        String field,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object rejectedValue,
        String message
) {
}
