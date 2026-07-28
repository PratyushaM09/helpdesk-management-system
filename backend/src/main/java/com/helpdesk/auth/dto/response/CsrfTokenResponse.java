package com.helpdesk.auth.dto.response;

/**
 * The payload {@code GET /api/v1/auth/csrf-token} returns, wrapped in
 * {@link com.helpdesk.common.ApiResponse} — see that endpoint's Javadoc on
 * {@code AuthenticationController} for why it exists.
 *
 * @param csrfToken the current CSRF double-submit value, matching the {@code csrf_token} cookie this same response sets
 */
public record CsrfTokenResponse(String csrfToken) {
}
