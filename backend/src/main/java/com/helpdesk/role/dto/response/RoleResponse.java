package com.helpdesk.role.dto.response;

import com.helpdesk.role.entity.RoleName;

import java.time.Instant;

/**
 * The payload every Role endpoint returns, wrapped in
 * {@link com.helpdesk.common.ApiResponse}.
 *
 * @param id          surrogate identifier
 * @param name        one of {@code USER}, {@code SUPPORT_ENGINEER}, {@code ADMIN}; immutable
 * @param description admin-editable summary of the role
 * @param system      {@code true} for every seeded role — a system role can never be
 *                    deleted, regardless of usage; exposed so a client can
 *                    proactively disable delete/rename affordances (the
 *                    server-side guard remains authoritative either way)
 * @param createdAt   when the role was seeded
 * @param updatedAt   when the role was last modified (e.g. its description)
 */
public record RoleResponse(
        Long id,
        RoleName name,
        String description,
        boolean system,
        Instant createdAt,
        Instant updatedAt
) {
}
