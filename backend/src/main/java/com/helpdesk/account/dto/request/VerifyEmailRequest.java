package com.helpdesk.account.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/account/verify-email} request body.
 * <p>
 * As with {@link ResetPasswordRequest#token()}, this is the raw, unhashed
 * token the caller received out of band; the Service layer hashes it before
 * comparing against the stored {@code tokenHash}.
 *
 * @param token the raw email verification token issued to the caller
 */
public record VerifyEmailRequest(
        @NotBlank
        String token
) {
}
