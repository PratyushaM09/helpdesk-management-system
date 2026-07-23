package com.helpdesk.security;

import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — {@link JwtService}/{@link CustomUserDetailsService} mocked,
 * real {@link MockHttpServletRequest}/{@link MockHttpServletResponse} (spring-test).
 * {@code SecurityContextHolder} is a ThreadLocal, cleared before and after
 * every test to prevent cross-test/cross-class pollution.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private CustomUserDetailsService customUserDetailsService;
    private JwtAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        customUserDetailsService = mock(CustomUserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticate_whenValidAccessTokenCookiePresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "valid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubValidToken("valid-token", 1L, RoleName.ADMIN, UserStatus.VERIFIED, 0);

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(1L, ((UserPrincipal) authentication.getPrincipal()).userId());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAuthenticate_whenValidBearerHeaderPresentAndNoCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, SecurityConstants.BEARER_PREFIX + "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubValidToken("valid-token", 2L, RoleName.USER, UserStatus.VERIFIED, 0);

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(2L, ((UserPrincipal) authentication.getPrincipal()).userId());
    }

    @Test
    void shouldPreferCookieOverHeader_whenBothPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "cookie-token"));
        request.addHeader(SecurityConstants.AUTHORIZATION_HEADER, SecurityConstants.BEARER_PREFIX + "header-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubValidToken("cookie-token", 3L, RoleName.USER, UserStatus.VERIFIED, 0);

        filter.doFilter(request, response, filterChain);

        verify(jwtService).parseClaims("cookie-token");
        verify(jwtService, never()).parseClaims("header-token");
    }

    @Test
    void shouldLeaveUnauthenticated_whenNoTokenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldLeaveUnauthenticated_whenTokenSignatureInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "bad-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.parseClaims("bad-token")).thenThrow(new JwtException("Invalid signature"));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldLeaveUnauthenticated_whenTokenVersionStale() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "stale-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("4");
        when(jwtService.parseClaims("stale-token")).thenReturn(claims);
        when(customUserDetailsService.loadUserById(4L)).thenReturn(aPrincipal(4L, RoleName.USER, UserStatus.VERIFIED, 2));
        when(jwtService.isTokenVersionCurrent(claims, 2)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldLeaveUnauthenticated_whenAccountLocked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubValidToken("token", 5L, RoleName.USER, UserStatus.LOCKED, 0);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldLeaveUnauthenticated_whenAccountDeactivated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        stubValidToken("token", 6L, RoleName.USER, UserStatus.DEACTIVATED, 0);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldLeaveUnauthenticated_whenUserNoLongerExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.ACCESS_TOKEN_COOKIE, "token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("999");
        when(jwtService.parseClaims("token")).thenReturn(claims);
        when(customUserDetailsService.loadUserById(999L)).thenThrow(new UsernameNotFoundException("gone"));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    private void stubValidToken(String token, Long userId, RoleName role, UserStatus status, int tokenVersion) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        when(jwtService.parseClaims(token)).thenReturn(claims);
        when(customUserDetailsService.loadUserById(userId)).thenReturn(aPrincipal(userId, role, status, tokenVersion));
        when(jwtService.isTokenVersionCurrent(claims, tokenVersion)).thenReturn(true);
    }

    private UserPrincipal aPrincipal(Long userId, RoleName role, UserStatus status, int tokenVersion) {
        return new UserPrincipal(userId, "user%d@example.com".formatted(userId), "hashed-password", role, status, tokenVersion);
    }
}
