package com.helpdesk.exception;

import org.springframework.http.HttpStatus;

/**
 * A requested resource does not exist, or — per the "404, never 403" rule
 * for ownership-scoped resources (07-Security-Architecture.md §4,
 * 09-Security-Operations.md §18.2) — exists but isn't visible to the
 * caller. Future modules should reuse this for both cases; a separate
 * "not visible to you" exception is not introduced.
 */
public class ResourceNotFoundException extends ApplicationException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, message, HttpStatus.NOT_FOUND);
    }

    /**
     * Convenience constructor for the common "X not found with Y: Z" shape,
     * e.g. {@code new ResourceNotFoundException("Ticket", "id", 42)}.
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(ERROR_CODE, "%s not found with %s: '%s'".formatted(resourceName, fieldName, fieldValue), HttpStatus.NOT_FOUND);
    }
}
