package com.helpdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filter chain position 3 — double-submit CSRF validation (SDR-007),
 * scoped specifically to cookie-authenticated, state-changing requests.
 * <p>
 * A request carrying no {@code csrf_token} cookie at all — a Bearer-header
 * client, or one that never logged in via the cookie path — is not a
 * CSRF-exposed request in the first place (CSRF is fundamentally a
 * browser-cookie-auth phenomenon, per SDR-002's Future Impact note) and is
 * deliberately let through unchecked: this filter's whole job is comparing
 * a cookie against a header, not deciding whether a request is otherwise
 * authenticated — that is the downstream authorization layer's job.
 * <p>
 * Deliberately not a {@code @Component}, same reasoning as
 * {@link JwtAuthenticationFilter} — constructed explicitly in
 * {@code SecurityConfig} and wired via {@code addFilterAfter}.
 */
public class CsrfValidationFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());

    private final AccessDeniedHandler accessDeniedHandler;

    public CsrfValidationFilter(AccessDeniedHandler accessDeniedHandler) {
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String cookieValue = extractCsrfCookie(request);
        if (cookieValue == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerValue = request.getHeader(SecurityConstants.CSRF_HEADER);
        if (!cookieValue.equals(headerValue)) {
            accessDeniedHandler.handle(request, response, new AccessDeniedException("CSRF token missing or invalid."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractCsrfCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SecurityConstants.CSRF_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
