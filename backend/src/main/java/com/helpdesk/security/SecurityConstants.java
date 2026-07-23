package com.helpdesk.security;

/**
 * Single source of truth for every literal name the Authentication module's
 * classes must agree on — a cookie/header/claim name existing as a typo'd
 * duplicate literal in two files is exactly the class of bug this exists to
 * prevent. Referenced by {@code JwtService}, {@code JwtAuthenticationFilter},
 * {@code CookieService}, and {@code CsrfValidationFilter} (none built yet).
 */
public final class SecurityConstants {

    // Cookies (07-Security-Architecture.md §5.8)
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String CSRF_COOKIE = "csrf_token";

    // Headers
    public static final String AUTHORIZATION_HEADER = "Authorization";
    /** Prefix stripped when extracting a token from {@link #AUTHORIZATION_HEADER} (SDR-002's alternative path). */
    public static final String BEARER_PREFIX = "Bearer ";
    /** Double-submit CSRF header (SDR-007) — the SPA echoes {@link #CSRF_COOKIE}'s value here. */
    public static final String CSRF_HEADER = "X-CSRF-Token";

    // Claims — custom claims only; registered claims (sub/iat/exp) are read
    // via the JWT library's own typed accessors, never a string key.
    public static final String ROLE_CLAIM = "role";
    public static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    private SecurityConstants() {
    }
}
