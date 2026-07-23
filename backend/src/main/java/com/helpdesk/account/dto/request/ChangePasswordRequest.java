package com.helpdesk.account.dto.request;

import com.helpdesk.validation.annotation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/v1/account/password} request body.
 * <p>
 * {@code currentPassword} is validated with only {@code @NotBlank}, never
 * {@code @StrongPassword} — it is being verified against an existing hash,
 * not set, so its strength is irrelevant (same reasoning as
 * {@code LoginRequest.password}). {@code newPassword} and
 * {@code confirmPassword} must be equal; that check is cross-field and is
 * performed by the Service layer rather than here, matching this codebase's
 * existing pattern of leaving cross-field/DB-dependent rules (e.g. email
 * uniqueness) out of Bean Validation.
 *
 * @param currentPassword the caller's existing plaintext password, checked
 *                         against the stored hash before anything changes
 * @param newPassword     plaintext replacement password, hashed by the
 *                         Service before persisting — never stored or
 *                         logged as received
 * @param confirmPassword repeat of {@code newPassword}; must match exactly
 */
public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @StrongPassword
        String newPassword,

        @NotBlank
        String confirmPassword
) {
}
