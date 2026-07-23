package com.helpdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pure unit test — {@link AccessDeniedHandler} mocked, real
 * {@link MockHttpServletRequest}/{@link MockHttpServletResponse}.
 */
class CsrfValidationFilterTest {

    private AccessDeniedHandler accessDeniedHandler;
    private CsrfValidationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        accessDeniedHandler = mock(AccessDeniedHandler.class);
        filter = new CsrfValidationFilter(accessDeniedHandler);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void shouldSkipCheck_whenMethodIsSafe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/roles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(accessDeniedHandler);
    }

    @Test
    void shouldAllow_whenNoCsrfCookiePresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/roles/1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(accessDeniedHandler);
    }

    @Test
    void shouldAllow_whenCookieAndHeaderMatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/roles/1");
        request.setCookies(new Cookie(SecurityConstants.CSRF_COOKIE, "matching-value"));
        request.addHeader(SecurityConstants.CSRF_HEADER, "matching-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(accessDeniedHandler);
    }

    @Test
    void shouldReject_whenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/roles/1");
        request.setCookies(new Cookie(SecurityConstants.CSRF_COOKIE, "cookie-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(accessDeniedHandler).handle(any(), any(), any(AccessDeniedException.class));
    }

    @Test
    void shouldReject_whenHeaderDoesNotMatchCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/roles/1");
        request.setCookies(new Cookie(SecurityConstants.CSRF_COOKIE, "cookie-value"));
        request.addHeader(SecurityConstants.CSRF_HEADER, "different-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(accessDeniedHandler).handle(any(), any(), any(AccessDeniedException.class));
    }
}
