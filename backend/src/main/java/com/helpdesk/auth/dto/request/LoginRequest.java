package com.helpdesk.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/login} request body (07-Security-Architecture.md §3.2).
 * <p>
 * {@code password} is deliberately validated with only {@code @NotBlank},
 * never {@code @StrongPassword} — login verifies a submitted value against
 * an already-stored hash, it does not judge the value's quality. Strength
 * is enforced only where a password is being *set* (registration, change,
 * reset), not here.
 *
 * @param email    the account's login identifier; see {@link com.helpdesk.user.dto.request.CreateUserRequest#email()}
 * @param password plaintext password, compared against the stored hash by
 *                 the Service layer — never stored or logged as received
 */
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        String password
) {
}
