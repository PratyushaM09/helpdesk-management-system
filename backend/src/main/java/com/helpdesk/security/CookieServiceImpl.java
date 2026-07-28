package com.helpdesk.security;

import com.helpdesk.constant.ApiConstants;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Every cookie this application issues shares {@code Secure} and
 * {@code SameSite=None} unconditionally (07-Security-Architecture.md
 * §5.8, revised — frontend and backend are deployed on different Render
 * subdomains, which the browser treats as different sites; {@code
 * SameSite=Strict}/{@code Lax} cookies are never sent on such cross-site
 * requests, so {@code None} is required for the SPA to stay authenticated
 * at all). CSRF defense no longer has {@code SameSite} as a redundant
 * second layer and now rests solely on the double-submit token
 * (SDR-007) — still a complete, independent defense on its own.
 * {@code Secure} is never conditionally omitted per profile; a {@code
 * dev} browser testing over {@code http://localhost} still works because
 * browsers themselves treat {@code localhost} as a secure context even
 * without TLS (the documented exception ADR-0003/SDR-002 rely on), not
 * because this service relaxes the flag.
 */
@Service
public class CookieServiceImpl implements CookieService {

    private static final String ACCESS_TOKEN_PATH = ApiConstants.API_BASE_PATH;
    private static final String REFRESH_TOKEN_PATH = ApiConstants.API_BASE_PATH + "/auth/refresh";
    private static final String CSRF_TOKEN_PATH = ApiConstants.API_BASE_PATH;

    private final JwtProperties jwtProperties;

    public CookieServiceImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public ResponseCookie createAccessTokenCookie(String accessToken) {
        return build(SecurityConstants.ACCESS_TOKEN_COOKIE, accessToken, ACCESS_TOKEN_PATH, true, jwtProperties.accessTokenTtl());
    }

    @Override
    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return build(SecurityConstants.REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_TOKEN_PATH, true, jwtProperties.refreshTokenTtl());
    }

    @Override
    public ResponseCookie createCsrfTokenCookie(String csrfToken) {
        // Lives as long as the refresh token it protects operations for, not just the access token's short TTL.
        return build(SecurityConstants.CSRF_COOKIE, csrfToken, CSRF_TOKEN_PATH, false, jwtProperties.refreshTokenTtl());
    }

    @Override
    public List<ResponseCookie> clearAllCookies() {
        return List.of(
                clear(SecurityConstants.ACCESS_TOKEN_COOKIE, ACCESS_TOKEN_PATH),
                clear(SecurityConstants.REFRESH_TOKEN_COOKIE, REFRESH_TOKEN_PATH),
                clear(SecurityConstants.CSRF_COOKIE, CSRF_TOKEN_PATH)
        );
    }

    private ResponseCookie build(String name, String value, String path, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(true)
                .sameSite("None")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clear(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }
}
