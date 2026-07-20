package com.helpdesk.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RejectedValueSanitizerTest {

    @Test
    void shouldRedactValue_whenFieldNameIsSensitive() {
        assertNull(RejectedValueSanitizer.sanitize("password", "hunter2"));
        assertNull(RejectedValueSanitizer.sanitize("newPassword", "hunter2"));
        assertNull(RejectedValueSanitizer.sanitize("apiToken", "abc123"));
        assertNull(RejectedValueSanitizer.sanitize("SSN", "123-45-6789"));
    }

    @Test
    void shouldReturnValue_whenFieldIsSafeAndTypeIsSimple() {
        assertEquals("bad-email", RejectedValueSanitizer.sanitize("email", "bad-email"));
        assertEquals(-5, RejectedValueSanitizer.sanitize("priorityLevel", -5));
        assertEquals(Boolean.TRUE, RejectedValueSanitizer.sanitize("active", Boolean.TRUE));
    }

    @Test
    void shouldRedact_whenValueIsNull() {
        assertNull(RejectedValueSanitizer.sanitize("title", null));
    }

    @Test
    void shouldRedact_whenValueIsAComplexType() {
        record ComplexPayload(String nested) {
        }
        assertNull(RejectedValueSanitizer.sanitize("payload", new ComplexPayload("secretish structure")));
    }
}
