package com.helpdesk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test — real {@link CookieServiceImpl}, deterministic, no mocks.
 */
class CookieServiceImplTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private CookieServiceImpl cookieService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("HS512", "irrelevant", null, null, ACCESS_TTL, REFRESH_TTL, "issuer");
        cookieService = new CookieServiceImpl(jwtProperties);
    }

    @Test
    void createAccessTokenCookie_shouldSetExpectedFlagsAndPath() {
        ResponseCookie cookie = cookieService.createAccessTokenCookie("token-value");

        assertEquals(SecurityConstants.ACCESS_TOKEN_COOKIE, cookie.getName());
        assertEquals("token-value", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals("/api/v1", cookie.getPath());
        assertEquals(ACCESS_TTL, cookie.getMaxAge());
    }

    @Test
    void createRefreshTokenCookie_shouldScopePathToRefreshEndpointOnly() {
        ResponseCookie cookie = cookieService.createRefreshTokenCookie("refresh-value");

        assertEquals(SecurityConstants.REFRESH_TOKEN_COOKIE, cookie.getName());
        assertTrue(cookie.isHttpOnly());
        assertEquals("/api/v1/auth/refresh", cookie.getPath());
        assertEquals(REFRESH_TTL, cookie.getMaxAge());
    }

    @Test
    void createCsrfTokenCookie_shouldNotBeHttpOnly_butStillSecureAndStrict() {
        ResponseCookie cookie = cookieService.createCsrfTokenCookie("csrf-value");

        assertEquals(SecurityConstants.CSRF_COOKIE, cookie.getName());
        assertFalse(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals(REFRESH_TTL, cookie.getMaxAge());
    }

    @Test
    void clearAllCookies_shouldReturnThreeCookies_withEmptyValuesAndZeroMaxAge() {
        List<ResponseCookie> cleared = cookieService.clearAllCookies();

        assertEquals(3, cleared.size());
        for (ResponseCookie cookie : cleared) {
            assertEquals("", cookie.getValue());
            assertEquals(Duration.ZERO, cookie.getMaxAge());
        }
        assertTrue(cleared.stream().anyMatch(c -> c.getName().equals(SecurityConstants.ACCESS_TOKEN_COOKIE)));
        assertTrue(cleared.stream().anyMatch(c -> c.getName().equals(SecurityConstants.REFRESH_TOKEN_COOKIE)));
        assertTrue(cleared.stream().anyMatch(c -> c.getName().equals(SecurityConstants.CSRF_COOKIE)));
    }
}
