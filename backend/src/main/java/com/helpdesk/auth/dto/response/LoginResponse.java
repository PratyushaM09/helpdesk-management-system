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
 * <p>
 * {@code csrfToken} is the one deliberate exception to "no security state
 * here": it is not secret (the cookie carrying the same value is already
 * non-{@code HttpOnly} by design, SDR-007), and a frontend on a different
 * subdomain than the API cannot read that cookie via {@code document.cookie}
 * at all — cookie visibility to JS is scoped to the setting origin,
 * independent of {@code SameSite}. Echoing the value here is what lets such
 * a frontend cache it in memory and echo it back as {@code X-CSRF-Token} on
 * subsequent state-changing calls. {@code null} on the {@code /auth/refresh}
 * reuse of this same DTO, since refresh deliberately does not rotate the
 * CSRF cookie (see {@code CookieServiceImpl.createCsrfTokenCookie}).
 *
 * @param id        surrogate identifier
 * @param name      display name
 * @param email     login identifier
 * @param role      one of {@code USER}, {@code SUPPORT_ENGINEER}, {@code ADMIN}
 * @param csrfToken the just-issued CSRF double-submit value; {@code null} when not (re)issued
 */
public record LoginResponse(
        Long id,
        String name,
        String email,
        RoleName role,
        String csrfToken
) {
}
