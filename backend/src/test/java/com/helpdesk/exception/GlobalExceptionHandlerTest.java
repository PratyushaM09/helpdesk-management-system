package com.helpdesk.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — no Spring context. GlobalExceptionHandler has zero
 * constructor dependencies, so it's instantiated directly, per
 * 11-Development-Rules.md §16.1's "unit test the Service/infra layer
 * without a Spring context" convention. Proves the status/errorCode
 * mapping for every exception type this milestone introduces, and that
 * an unanticipated failure never leaks its own message to the client.
 */
class GlobalExceptionHandlerTest {

    private static final String REQUEST_PATH = "/api/v1/tickets/42";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(REQUEST_PATH);
    }

    @Test
    void shouldMapResourceNotFoundException_to404() {
        var ex = new ResourceNotFoundException("Ticket", "id", 42);

        ErrorResponse body = handleAndGetBody(handler.handleApplicationException(ex, request), HttpStatus.NOT_FOUND);

        assertEquals("RESOURCE_NOT_FOUND", body.errorCode());
        assertEquals("Ticket not found with id: '42'", body.message());
        assertEquals(REQUEST_PATH, body.path());
    }

    @Test
    void shouldMapBadRequestException_to400() {
        var ex = new BadRequestException("Reason cannot be blank.");

        ErrorResponse body = handleAndGetBody(handler.handleApplicationException(ex, request), HttpStatus.BAD_REQUEST);

        assertEquals("BAD_REQUEST", body.errorCode());
    }

    @Test
    void shouldMapConflictException_to409() {
        var ex = new ConflictException("This ticket was updated by someone else.");

        ErrorResponse body = handleAndGetBody(handler.handleApplicationException(ex, request), HttpStatus.CONFLICT);

        assertEquals("CONFLICT", body.errorCode());
    }

    @Test
    void shouldMapUnauthorizedException_to401() {
        var ex = new UnauthorizedException("Authentication required.");

        ErrorResponse body = handleAndGetBody(handler.handleApplicationException(ex, request), HttpStatus.UNAUTHORIZED);

        assertEquals("UNAUTHORIZED", body.errorCode());
    }

    @Test
    void shouldMapForbiddenException_to403() {
        var ex = new ForbiddenException("Not permitted to perform this action.");

        ErrorResponse body = handleAndGetBody(handler.handleApplicationException(ex, request), HttpStatus.FORBIDDEN);

        assertEquals("FORBIDDEN", body.errorCode());
    }

    @Test
    void shouldMapUnexpectedRuntimeException_to500WithGenericMessage_neverLeakingRawMessage() {
        var ex = new IllegalStateException("Connection to internal-db-host:5432 refused");

        ErrorResponse body = handleAndGetBody(handler.handleRuntimeException(ex, request), HttpStatus.INTERNAL_SERVER_ERROR);

        assertEquals("INTERNAL_SERVER_ERROR", body.errorCode());
        assertEquals("Something went wrong. Please try again later.", body.message());
    }

    @Test
    void shouldMapUnexpectedCheckedException_to500WithGenericMessage() {
        var ex = new Exception("some internal detail that must never reach the client");

        ErrorResponse body = handleAndGetBody(handler.handleException(ex, request), HttpStatus.INTERNAL_SERVER_ERROR);

        assertEquals("Something went wrong. Please try again later.", body.message());
    }

    @Test
    void shouldMapBindException_to400WithFieldLevelErrorsAndRedactSensitiveValues() {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "createTicketRequest");
        bindingResult.addError(new FieldError("createTicketRequest", "title", "", false, null, null, "Title is required."));
        bindingResult.addError(new FieldError("createTicketRequest", "password", "hunter2", false, null, null, "Password does not meet strength requirements."));
        var ex = new BindException(bindingResult);

        ErrorResponse body = handleAndGetBody(handler.handleBindException(ex, request), HttpStatus.BAD_REQUEST);

        assertEquals("VALIDATION_FAILED", body.errorCode());
        assertEquals(2, body.validationErrors().size());

        ValidationError titleError = findByField(body.validationErrors(), "title");
        assertEquals("", titleError.rejectedValue());
        assertEquals("Title is required.", titleError.message());

        ValidationError passwordError = findByField(body.validationErrors(), "password");
        assertNull(passwordError.rejectedValue(), "password's rejected value must never be echoed back");
    }

    @Test
    void shouldMapMethodArgumentNotValidException_to400() throws NoSuchMethodException {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "createTicketRequest");
        bindingResult.addError(new FieldError("createTicketRequest", "categoryId", null, false, null, null, "Category is required."));
        Method dummyMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyControllerMethod", String.class);
        var ex = new MethodArgumentNotValidException(new MethodParameter(dummyMethod, 0), bindingResult);

        ErrorResponse body = handleAndGetBody(handler.handleBindException(ex, request), HttpStatus.BAD_REQUEST);

        assertEquals("VALIDATION_FAILED", body.errorCode());
        assertEquals("categoryId", body.validationErrors().get(0).field());
    }

    @Test
    void shouldMapConstraintViolationException_to400WithLeafFieldName() {
        Path propertyPath = mock(Path.class);
        when(propertyPath.toString()).thenReturn("assignTicket.engineerId");
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenAnswer(invocation -> propertyPath);
        when(violation.getMessage()).thenReturn("must not be null");
        when(violation.getInvalidValue()).thenReturn(null);
        var ex = new ConstraintViolationException(Set.of(violation));

        ErrorResponse body = handleAndGetBody(handler.handleConstraintViolationException(ex, request), HttpStatus.BAD_REQUEST);

        assertEquals("VALIDATION_FAILED", body.errorCode());
        assertEquals("engineerId", body.validationErrors().get(0).field());
        assertEquals("must not be null", body.validationErrors().get(0).message());
    }

    @Test
    void shouldMapHttpMessageNotReadableException_to400WithoutLeakingParseDetail() {
        var ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character ('}' (code 125))", mock(HttpInputMessage.class));

        ErrorResponse body = handleAndGetBody(handler.handleHttpMessageNotReadableException(ex, request), HttpStatus.BAD_REQUEST);

        assertEquals("MALFORMED_REQUEST_BODY", body.errorCode());
        assertEquals("The request body could not be read. Please check its syntax.", body.message());
        assertTrue(body.validationErrors() == null || body.validationErrors().isEmpty());
    }

    private static ValidationError findByField(List<ValidationError> errors, String field) {
        return errors.stream()
                .filter(error -> error.field().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No validation error found for field: " + field));
    }

    @SuppressWarnings("unused")
    private void dummyControllerMethod(String value) {
        // Exists only so a real java.lang.reflect.Method is available to
        // construct a MethodParameter for MethodArgumentNotValidException
        // in the test above - never called.
    }

    private static ErrorResponse handleAndGetBody(ResponseEntity<ErrorResponse> response, HttpStatus expectedStatus) {
        assertEquals(expectedStatus, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        return body;
    }
}
