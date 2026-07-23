package com.helpdesk.account.dto.response;

import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.UserStatus;

import java.time.Instant;

/**
 * The payload returned by {@code GET /api/v1/account/me} and
 * {@code PUT /api/v1/account/profile}, wrapped in
 * {@link com.helpdesk.common.ApiResponse}. Deliberately excludes
 * {@code passwordHash}, refresh tokens, {@code tokenVersion}, and lockout
 * fields ({@code failedAttempts}, {@code lockedUntil}) — this DTO is
 * allow-listed by construction, the same convention as
 * {@code UserResponse}, so none of those entity fields become visible
 * unless someone deliberately adds a mapping line for them.
 *
 * @param id            surrogate identifier
 * @param name          display name
 * @param email         unique login identifier
 * @param role          one of {@code USER}, {@code SUPPORT_ENGINEER}, {@code ADMIN}
 * @param status        account lifecycle state ({@code ACTIVE}, {@code LOCKED},
 *                      {@code DEACTIVATED}) — independent of email verification
 * @param emailVerified whether the caller has confirmed their email address;
 *                      tracked separately from {@code status} (Milestone 4
 *                      design decision — verification is not an account
 *                      lifecycle state)
 * @param createdAt     when the account was created
 */
public record AccountProfileResponse(
        Long id,
        String name,
        String email,
        RoleName role,
        UserStatus status,
        boolean emailVerified,
        Instant createdAt
) {
}
