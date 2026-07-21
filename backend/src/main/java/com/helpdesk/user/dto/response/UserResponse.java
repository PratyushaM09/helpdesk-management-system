package com.helpdesk.user.dto.response;

import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.UserStatus;

import java.time.Instant;

/**
 * The payload every User endpoint returns, wrapped in
 * {@link com.helpdesk.common.ApiResponse}. Deliberately excludes the
 * entity's {@code passwordHash} — a DTO is allow-listed by construction, so
 * a sensitive entity field stays invisible here unless someone deliberately
 * adds a mapping line for it (11-Development-Rules.md §5.5), which this
 * record never does.
 *
 * @param id        surrogate identifier
 * @param name      display name
 * @param email     unique login identifier
 * @param role      one of {@code USER}, {@code SUPPORT_ENGINEER}, {@code ADMIN}
 * @param status    account lifecycle state; {@code DEACTIVATED} is a visible,
 *                  filterable status here, not a hidden-row soft delete
 *                  (see {@code UserRepository}'s Javadoc)
 * @param createdAt when the account was created
 * @param updatedAt when the account was last modified
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        RoleName role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
