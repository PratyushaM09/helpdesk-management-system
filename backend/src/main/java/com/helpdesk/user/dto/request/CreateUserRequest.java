package com.helpdesk.user.dto.request;

import com.helpdesk.role.entity.RoleName;
import com.helpdesk.validation.annotation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/users} request body.
 * <p>
 * {@code role} is accepted directly from the client in this milestone —
 * there is no authenticated caller yet to distinguish "self-registration"
 * from "admin-provisioned," so the client-can't-set-their-own-role rule
 * (03-Security.md §3.1) is deferred to the Authentication/Authorization
 * milestones. Typing it as {@link RoleName} rather than a raw {@code String}
 * means an unrecognized value fails JSON deserialization before Bean
 * Validation even runs — surfaced by the existing
 * {@code GlobalExceptionHandler}'s malformed-request-body handler, so no
 * separate enum-value validator is needed for something the type system
 * already guarantees.
 *
 * @param name     display name (entity column {@code name}, max 100)
 * @param email    unique login identifier (entity column {@code email}, max 255);
 *                 uniqueness itself is a Service-layer check, not expressible as
 *                 Bean Validation
 * @param password plaintext password, hashed by the Service before persisting —
 *                  never stored or logged as received
 * @param role     one of {@code USER}, {@code SUPPORT_ENGINEER}, {@code ADMIN}
 */
public record CreateUserRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @StrongPassword
        String password,

        @NotNull
        RoleName role
) {
}
