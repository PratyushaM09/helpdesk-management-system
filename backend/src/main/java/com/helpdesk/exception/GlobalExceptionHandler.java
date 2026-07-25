package com.helpdesk.exception;

import com.helpdesk.constant.ResponseMessages;
import com.helpdesk.util.RejectedValueSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

/**
 * The single place an exception becomes an HTTP response
 * (02-Architecture.md §12) — no controller ever builds its own error body.
 * Handler tiers, most-specific-first (Spring resolves by type specificity,
 * not declaration order, but the ordering below mirrors that resolution):
 * <ol>
 *   <li>{@link ApplicationException} — an expected, already-classified
 *       failure; the exception itself carries the correct HTTP status,
 *       error code, and client-safe message.</li>
 *   <li>{@link AccessDeniedException} (Phase 2, Milestone 5/6) — a
 *       {@code @PreAuthorize} check on a Controller method denied access,
 *       almost always its {@link AuthorizationDeniedException} subtype
 *       specifically. Handled by the superclass, not the subtype, so any
 *       future method-security annotation that throws a differently-typed
 *       {@code AccessDeniedException} (e.g. a hypothetical {@code
 *       @PostAuthorize}/{@code @PreFilter} adoption) is covered by
 *       construction rather than needing its own handler added later.
 *       Unlike the URL-matcher-based checks {@code SecurityConfig} still
 *       performs for the blanket "must be authenticated" rule, this
 *       exception is thrown from *inside* {@code DispatcherServlet}'s
 *       dispatch (the method-security AOP interceptor wraps the actual
 *       controller method call), so it never reaches
 *       {@code ExceptionTranslationFilter}/{@code RestAccessDeniedHandler}
 *       — this handler is the equivalent for that boundary, built to
 *       return the byte-for-byte identical body so a client can't tell
 *       which layer rejected the request.</li>
 *   <li>{@link AuthenticationException} (Phase 2, Milestone 6, defensive) —
 *       no currently-reachable code path throws this from inside
 *       {@code DispatcherServlet}'s dispatch (anonymous authentication is
 *       always populated by the filter chain, so a {@code @PreAuthorize}
 *       SpEL check always has a non-null, if anonymous, principal to
 *       evaluate against). Handled anyway, at the same tier as {@link
 *       AccessDeniedException}, for the same reason: a future
 *       authentication-adjacent check thrown from inside dispatch should
 *       already have a home here rather than falling through to the
 *       generic 500 handler the way {@code AuthorizationDeniedException}
 *       originally did before this milestone.</li>
 *   <li>{@link MethodArgumentNotValidException} / {@link BindException} —
 *       Bean Validation failed on a {@code @Valid}-bound request body or
 *       form/query object. {@code MethodArgumentNotValidException} IS a
 *       {@code BindException} (JSON body binding is a specialization of
 *       the same failure), so one handler method, registered for both
 *       types, covers each without duplicating the field-extraction logic.</li>
 *   <li>{@link ConstraintViolationException} — method-level validation
 *       (a {@code @Validated} class with a constrained {@code @PathVariable}/
 *       {@code @RequestParam}, or a {@code @Validated} Service method).</li>
 *   <li>{@link HttpMessageNotReadableException} — the request body couldn't
 *       even be parsed, so there is no bound object to report field errors
 *       against; a fixed, safe message only.</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — a path/query value
 *       couldn't be converted to the controller method's declared parameter
 *       type (e.g. a non-numeric id). A {@code RuntimeException}, so without
 *       an explicit handler it fell through to the generic 500 tier despite
 *       being exactly as client-caused as a Bean Validation failure - mapped
 *       to the same {@code ValidationError} shape as {@link BindException}
 *       above for a consistent client-facing contract.</li>
 *   <li>{@link MissingServletRequestPartException} — a multipart request is
 *       missing a required part (e.g. no {@code file} field at all, as
 *       opposed to an oversized one). Same "argument-resolution-time,
 *       checked {@code ServletException}" reasoning as
 *       {@link MaxUploadSizeExceededException} below.</li>
 *   <li>{@link MaxUploadSizeExceededException} — a multipart upload
 *       exceeded {@code spring.servlet.multipart.max-file-size}/
 *       {@code max-request-size}. Thrown while Spring MVC resolves the
 *       {@code MultipartFile} controller-method argument, so — like
 *       {@link AccessDeniedException} above — it never reaches the
 *       controller body itself. It is a checked {@code ServletException},
 *       not a {@code RuntimeException}, so without an explicit handler
 *       here it would fall through to the generic 500 tier below.</li>
 *   <li>{@link RuntimeException} / {@link Exception} — an unanticipated
 *       failure. Logged with full detail server-side; the client gets a
 *       generic, safe message.</li>
 * </ol>
 * <p>
 * <b>First exercised by a real endpoint in Phase 2 Milestone 1:</b> this
 * class was originally built ahead of any controller needing it (task
 * objective: "every future Request DTO should be able to use this
 * validation system without modification"). {@code UserController}'s
 * {@code @Valid}-annotated {@code CreateUserRequest}/{@code UpdateUserRequest}
 * parameters are the first live callers of the
 * {@link MethodArgumentNotValidException} handler below — proof that
 * promise held without any change to this handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_FAILED";
    private static final String MALFORMED_REQUEST_ERROR_CODE = "MALFORMED_REQUEST_BODY";

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex, HttpServletRequest request) {
        // Expected, client-caused outcome — WARN, no stack trace. Never
        // ERROR: an ERROR log implies engineering attention is needed,
        // which a routine 404/400/409 does not (09-Security-Operations.md §16.1).
        log.warn("{} [{}]: {} ({})", ex.getClass().getSimpleName(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return buildResponse(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        // Expected, client-caused outcome, same as ApplicationException
        // above - WARN, no stack trace.
        log.warn("{} [FORBIDDEN]: access denied ({})", ex.getClass().getSimpleName(), request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ResponseMessages.ACCESS_DENIED, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        // Same "expected, client-caused" reasoning as handleAccessDeniedException -
        // see this class's Javadoc for why no currently-reachable path throws
        // this specific type here today.
        log.warn("{} [UNAUTHORIZED]: authentication required ({})", ex.getClass().getSimpleName(), request.getRequestURI());
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ResponseMessages.AUTHENTICATION_REQUIRED, request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex, HttpServletRequest request) {
        List<ValidationError> validationErrors = extractFieldErrors(ex.getBindingResult());
        // A validation failure is a client error, never ERROR (task 7) —
        // no stack trace; the field list itself is the useful diagnostic.
        log.warn("Validation failed at {}: {} field error(s)", request.getRequestURI(), validationErrors.size());
        return buildValidationResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_CODE, ResponseMessages.VALIDATION_FAILED, request, validationErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        List<ValidationError> validationErrors = extractConstraintViolations(ex);
        log.warn("Constraint violation at {}: {} violation(s)", request.getRequestURI(), validationErrors.size());
        return buildValidationResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_CODE, ResponseMessages.VALIDATION_FAILED, request, validationErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // No bound object exists at this point in the request lifecycle -
        // nothing to extract field errors from. The raw parse-exception
        // message is never surfaced: it can echo fragments of the
        // malformed payload back, which is both unhelpful and a minor
        // internal-detail leak (09-Security-Operations.md §18.1).
        log.warn("Malformed request body at {}", request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST_ERROR_CODE, ResponseMessages.MALFORMED_REQUEST_BODY, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        // A path/query value that couldn't be converted to the controller
        // method's declared parameter type (e.g. "/tickets/not-a-number" -
        // Long expected). Thrown while Spring MVC resolves the method's
        // arguments, before the controller body runs, so - like
        // MaxUploadSizeExceededException below - it never reaches the
        // Service layer's own validation. A RuntimeException, so without
        // this handler it fell through to the generic 500 tier, even though
        // it's exactly as client-caused as a Bean Validation failure.
        String field = ex.getName();
        ValidationError error = new ValidationError(field, RejectedValueSanitizer.sanitize(field, ex.getValue()), "must be a valid value");
        log.warn("Type mismatch at {}: parameter '{}'", request.getRequestURI(), field);
        return buildValidationResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_CODE, ResponseMessages.VALIDATION_FAILED, request, List.of(error));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(MissingServletRequestPartException ex, HttpServletRequest request) {
        // A multipart request missing a @RequestParam("file") MultipartFile
        // part entirely (as opposed to an oversized one, which
        // MaxUploadSizeExceededException below already covers). Same
        // "thrown during argument resolution, checked ServletException,
        // falls through to 500 without this handler" reasoning.
        log.warn("{} at {}: missing part '{}'", ex.getClass().getSimpleName(), request.getRequestURI(), ex.getRequestPartName());
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Required part '%s' is missing.".formatted(ex.getRequestPartName()), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        // Thrown while Spring MVC resolves the MultipartFile argument, before
        // the controller method runs — an expected, client-caused outcome
        // like ApplicationException above, not an unanticipated failure.
        // It extends ServletException (checked), not RuntimeException, so
        // without this handler it fell through to the generic 500 tier.
        log.warn("{} at {}", ex.getClass().getSimpleName(), request.getRequestURI());
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", ResponseMessages.FILE_TOO_LARGE, request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        return handleUnexpected(ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
        return handleUnexpected(ex, request);
    }

    private ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Truly unanticipated — full stack trace, server-side only. Never
        // returned to the client (09-Security-Operations.md §18.1): no
        // exception.getMessage() passthrough, no stack trace, no internal
        // class/framework/SQL detail.
        log.error("Unhandled {} at {}", ex.getClass().getSimpleName(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", ResponseMessages.UNEXPECTED_ERROR, request);
    }

    private List<ValidationError> extractFieldErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fieldError -> new ValidationError(
                        fieldError.getField(),
                        RejectedValueSanitizer.sanitize(fieldError.getField(), fieldError.getRejectedValue()),
                        fieldError.getDefaultMessage()))
                .toList();
    }

    private List<ValidationError> extractConstraintViolations(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(violation -> {
                    String field = leafPropertyName(violation.getPropertyPath().toString());
                    return new ValidationError(
                            field,
                            RejectedValueSanitizer.sanitize(field, violation.getInvalidValue()),
                            violation.getMessage());
                })
                .toList();
    }

    /** "createTicket.title" / "someForm.title" → "title"; a bare "title" is returned unchanged. */
    private String leafPropertyName(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String errorCode, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, errorCode, message, request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> buildValidationResponse(HttpStatus status, String errorCode, String message,
                                                                    HttpServletRequest request, List<ValidationError> validationErrors) {
        return ResponseEntity.status(status).body(ErrorResponse.ofValidation(status, errorCode, message, request.getRequestURI(), validationErrors));
    }
}
