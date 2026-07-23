package com.helpdesk.account.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/account/forgot-password} request body.
 * <p>
 * Whether {@code email} belongs to an existing account is never surfaced to
 * the caller — the Service layer returns the same response regardless, so
 * this DTO only validates that the value is a well-formed email, not that it
 * resolves to a user.
 *
 * @param email the address to send a reset link to, if it belongs to an
 *              account; same format/length bounds as
 *              {@code CreateUserRequest.email}
 */
public record ForgotPasswordRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {
}
