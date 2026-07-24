package com.helpdesk.validation.validator;

import com.helpdesk.validation.annotation.StrongPassword;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Implements the {@link StrongPassword} contract. Pure, stateless, and
 * trivially unit-testable without a Spring context — per
 * 11-Development-Rules.md §16.1.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 10;
    // OWASP ASVS 2.1.2-shaped ceiling (Milestone 6 hardening): a raw
    // password this long has no legitimate strength benefit over one at
    // the ceiling, and accepting it unbounded is a cheap denial-of-service
    // vector — BCrypt's own per-hash cost is roughly linear in input
    // length below its 72-byte truncation point, so a client can force
    // disproportionate server-side work with an arbitrarily large payload
    // otherwise.
    private static final int MAX_LENGTH = 128;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SYMBOL = Pattern.compile("[^A-Za-z0-9]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Presence is @NotBlank's concern, not this constraint's — a null
        // value is valid here so the two failures are reported distinctly.
        if (value == null) {
            return true;
        }
        return value.length() >= MIN_LENGTH
                && value.length() <= MAX_LENGTH
                && UPPERCASE.matcher(value).find()
                && LOWERCASE.matcher(value).find()
                && DIGIT.matcher(value).find()
                && SYMBOL.matcher(value).find();
    }
}
