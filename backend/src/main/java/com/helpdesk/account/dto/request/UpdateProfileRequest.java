package com.helpdesk.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/account/profile} request body.
 * <p>
 * Deliberately carries only {@code name} — email, role, status, and password
 * are not components of this record at all, so the "profile update can't
 * touch those fields" rule is enforced by the DTO's shape rather than by
 * explicit rejection logic in the Service layer.
 *
 * @param name display name to replace the caller's current
 *             {@link com.helpdesk.user.entity.User#getName()} (max 100, same
 *             bound as {@code CreateUserRequest.name})
 */
public record UpdateProfileRequest(
        @NotBlank
        @Size(max = 100)
        String name
) {
}
