package com.helpdesk.user.dto.request;

import com.helpdesk.user.entity.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/users/{id}} request body.
 * <p>
 * Deliberately excludes {@code password} and {@code status}: password
 * change is its own Account Features flow (Milestone 4, with its own
 * current-password re-verification rule, 07-Security-Architecture.md §3.6),
 * and status only changes via the dedicated deactivate action
 * ({@code DELETE /api/v1/users/{id}}) in this milestone — collapsing every
 * updatable field into one request DTO would let a caller flip status as a
 * side effect of an unrelated profile edit.
 *
 * @param name  see {@link CreateUserRequest#name()}
 * @param email see {@link CreateUserRequest#email()}
 * @param role  see {@link CreateUserRequest#role()}
 */
public record UpdateUserRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotNull
        RoleName role
) {
}
