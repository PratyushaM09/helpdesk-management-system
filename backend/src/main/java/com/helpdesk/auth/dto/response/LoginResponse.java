package com.helpdesk.auth.dto.response;

import com.helpdesk.role.entity.RoleName;

/**
 * The payload {@code POST /api/v1/auth/login} returns, wrapped in
 * {@link com.helpdesk.common.ApiResponse}. Deliberately minimal — the
 * access and refresh tokens travel only as {@code HttpOnly} cookies
 * (SDR-002), never in this body, and no internal security state
 * ({@code failedAttempts}, {@code tokenVersion}, account status) is exposed
 * here either: this is an acknowledgment of who the caller authenticated
 * as, not a resource view (contrast with the fuller
 * {@link com.helpdesk.user.dto.response.UserResponse}, which this
 * deliberately does not reuse — see the Authentication module's own
 * design notes on why the two stay separate DTOs).
 *
 * @param id    surrogate identifier
 * @param name  display name
 * @param email login identifier
 * @param role  one of {@code USER}, {@code SUPPORT_ENGINEER}, {@code ADMIN}
 */
public record LoginResponse(
        Long id,
        String name,
        String email,
        RoleName role
) {
}
