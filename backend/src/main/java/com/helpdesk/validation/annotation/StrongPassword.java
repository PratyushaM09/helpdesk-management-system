package com.helpdesk.validation.annotation;

import com.helpdesk.validation.validator.StrongPasswordValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces the password strength policy already fixed in
 * 07-Security-Architecture.md §6 / SDR-001: minimum 10 characters, at
 * least one uppercase letter, one lowercase letter, one digit, and one
 * symbol. This is the one concrete constraint that policy names
 * ("07-Security-Architecture.md §3.1: enforced by a custom @StrongPassword
 * Bean Validation constraint") — implemented now, under that exact name,
 * as this milestone's demonstration custom validator rather than inventing
 * a differently-named equivalent.
 * <p>
 * Does not validate presence — apply alongside {@code @NotBlank} on the
 * same field; a {@code null} value is treated as valid here so the two
 * concerns (is it present vs. is it strong) fail with their own distinct,
 * correct message rather than one constraint doing both jobs.
 * <p>
 * Every future Request DTO with a raw password field (registration,
 * password change, password reset — FR-AUTH-1/5/6) uses this directly:
 * <pre>{@code
 *   @NotBlank
 *   @StrongPassword
 *   private String password;
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
@Documented
public @interface StrongPassword {

    String message() default "{validation.password.strength}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
