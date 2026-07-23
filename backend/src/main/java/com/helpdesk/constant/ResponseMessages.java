package com.helpdesk.constant;

/**
 * Generic, resource-agnostic response message strings shared by
 * {@code ApiResponse} and {@code GlobalExceptionHandler}. Deliberately
 * contains nothing specific to a future module (no "ticket created", etc.)
 * — a module introduces its own message constants alongside its own
 * feature code, it does not extend this class.
 */
public final class ResponseMessages {

    public static final String SUCCESS = "Operation completed successfully.";
    public static final String CREATED = "Resource created successfully.";
    public static final String UPDATED = "Resource updated successfully.";
    public static final String DELETED = "Resource deleted successfully.";

    /**
     * Deliberately vague — per 09-Security-Operations.md §18.1 / SDR-009,
     * a client-facing message is never built from a raw exception message
     * for an unanticipated failure. Full detail goes to the server-side log
     * only (see GlobalExceptionHandler).
     */
    public static final String UNEXPECTED_ERROR = "Something went wrong. Please try again later.";

    /** Top-level message for a field-validation failure; per-field detail lives in ErrorResponse.validationErrors. */
    public static final String VALIDATION_FAILED = "Validation failed. Please check the highlighted fields.";

    /**
     * For HttpMessageNotReadableException — the request body couldn't even
     * be parsed, so there's no bound object to report field errors against.
     * Never echoes the underlying parse exception's message (it can quote
     * fragments of the malformed JSON back, which is both unhelpful and a
     * minor internal-detail leak).
     */
    public static final String MALFORMED_REQUEST_BODY = "The request body could not be read. Please check its syntax.";

    /**
     * For an unauthenticated request to a protected route, written directly
     * by {@code RestAuthenticationEntryPoint} — the one 401 case
     * {@code GlobalExceptionHandler} never sees, since it's rejected inside
     * the security filter chain, before {@code DispatcherServlet} runs.
     */
    public static final String AUTHENTICATION_REQUIRED = "Authentication is required to access this resource.";

    /**
     * For an authenticated-but-wrong-role request, or a CSRF token
     * mismatch, written by {@code RestAccessDeniedHandler} — same "rejected
     * inside the filter chain" reasoning as {@link #AUTHENTICATION_REQUIRED}.
     */
    public static final String ACCESS_DENIED = "You do not have permission to perform this action.";

    private ResponseMessages() {
    }
}
