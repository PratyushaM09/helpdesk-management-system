package com.helpdesk.account.dto.request;

import com.helpdesk.validation.annotation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/account/reset-password} request body.
 * <p>
 * {@code token} is the raw, unhashed value the caller received out of band
 * (e.g. via a reset link); the Service layer hashes it and compares against
 * the stored {@code tokenHash} — the raw value itself is never persisted.
 * As with {@link ChangePasswordRequest}, {@code newPassword} and
 * {@code confirmPassword} equality is a Service-layer check, not a Bean
 * Validation constraint.
 *
 * @param token           the raw password reset token issued to the caller
 * @param newPassword     plaintext replacement password, hashed by the
 *                        Service before persisting — never stored or logged
 *                        as received
 * @param confirmPassword repeat of {@code newPassword}; must match exactly
 */
public record ResetPasswordRequest(
        @NotBlank
        String token,

        @NotBlank
        @StrongPassword
        String newPassword,

        @NotBlank
        String confirmPassword
) {
}
