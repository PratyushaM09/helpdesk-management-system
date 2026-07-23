package com.helpdesk.security;

import org.springframework.http.ResponseCookie;

import java.util.List;

/**
 * Centralizes every {@code Set-Cookie} this application ever issues
 * (07-Security-Architecture.md §5.8) — the one place cookie flags are
 * decided, so {@code HttpOnly}/{@code Secure}/{@code SameSite}/{@code Path}
 * are never re-typed (and potentially re-typed inconsistently) at more than
 * one call site. Knows nothing about JWTs or claims — every {@code create*}
 * method takes an already-produced opaque value and wraps it.
 */
public interface CookieService {

    /** {@code HttpOnly}, scoped to {@code /api} — carries the access token (SDR-002). */
    ResponseCookie createAccessTokenCookie(String accessToken);

    /** {@code HttpOnly}, narrowly scoped to the refresh endpoint only (SDR-002's minimized-exposure Path). */
    ResponseCookie createRefreshTokenCookie(String refreshToken);

    /** Deliberately <b>not</b> {@code HttpOnly} — must be JS-readable for the SPA to echo it into the CSRF header (SDR-007). */
    ResponseCookie createCsrfTokenCookie(String csrfToken);

    /** All three cookies, each with an empty value and immediate expiry — logout always clears them together, never selectively. */
    List<ResponseCookie> clearAllCookies();
}
