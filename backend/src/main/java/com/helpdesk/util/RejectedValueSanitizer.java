package com.helpdesk.util;

import java.util.Locale;
import java.util.Set;

/**
 * Decides what's safe to echo back in {@code ErrorResponse.validationErrors[].rejectedValue}
 * (task 3: "avoid exposing sensitive values"). Reused by every validation
 * extraction path in {@code GlobalExceptionHandler} — {@code BindException}
 * -based (Bean Validation on a request body) and
 * {@code ConstraintViolationException}-based (method-level validation) —
 * which is what justifies pulling it out as its own class rather than a
 * private method on the handler (11-Development-Rules.md §1: duplication
 * across two call sites of a genuine rule is a liability, not a
 * convenience).
 * <p>
 * Pure, stateless, dependency-free — trivially unit-testable.
 */
public final class RejectedValueSanitizer {

    /**
     * Field-name substrings (case-insensitive) whose value is never
     * echoed back, regardless of type. Matches the spirit of the
     * "never log/expose sensitive information" rule
     * (09-Security-Operations.md §16.2, 11-Development-Rules.md §13.3)
     * extended from logs to API error responses.
     */
    private static final Set<String> SENSITIVE_FIELD_KEYWORDS = Set.of(
            "password", "secret", "token", "pin", "ssn", "creditcard", "cvv"
    );

    private RejectedValueSanitizer() {
    }

    /**
     * @return {@code rejectedValue} if it's safe to expose (a simple type,
     *         from a non-sensitive field), otherwise {@code null}.
     */
    public static Object sanitize(String fieldName, Object rejectedValue) {
        if (rejectedValue == null || isSensitiveField(fieldName)) {
            return null;
        }
        return isSimpleType(rejectedValue) ? rejectedValue : null;
    }

    private static boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lowerCaseFieldName = fieldName.toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_KEYWORDS.stream().anyMatch(lowerCaseFieldName::contains);
    }

    private static boolean isSimpleType(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value.getClass().isEnum();
    }
}
