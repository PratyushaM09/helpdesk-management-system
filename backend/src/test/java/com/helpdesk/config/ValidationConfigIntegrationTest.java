package com.helpdesk.config;

import com.helpdesk.validation.annotation.StrongPassword;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one genuine integration test in this milestone (real Spring context,
 * not a mocked collaborator) — everything else here is either a pure unit
 * test or itself a data carrier. It exists specifically to prove task 1
 * ("ensure Jakarta Bean Validation is correctly configured") and task 5
 * ("messages moved to messages.properties") actually work wired together
 * through the real {@link ValidationConfig} bean, not just that each piece
 * is individually plausible in isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
class ValidationConfigIntegrationTest {

    @Autowired
    private Validator validator;

    private record PasswordHolder(@StrongPassword String password) {
    }

    @Test
    void strongPasswordViolationMessage_shouldResolveFromMessagesPropertiesNotAHibernateDefault() {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder("weak"));

        assertEquals(1, violations.size());
        String resolvedMessage = violations.iterator().next().getMessage();
        assertEquals(
                "Password must be at least 10 characters long and include at least one uppercase letter, "
                        + "one lowercase letter, one digit, and one symbol.",
                resolvedMessage);
    }

    @Test
    void strongPassword_shouldAcceptACompliantValue() {
        Set<ConstraintViolation<PasswordHolder>> violations = validator.validate(new PasswordHolder("Str0ng!Passw0rd"));

        assertTrue(violations.isEmpty());
    }
}
