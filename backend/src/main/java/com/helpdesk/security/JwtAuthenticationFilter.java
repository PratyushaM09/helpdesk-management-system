package com.helpdesk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter chain position 2 (see the conversation's filter-chain diagram) —
 * authenticates only, never rejects. Extracts a token from the access-token
 * cookie (SDR-002's primary path) or, failing that, the
 * {@code Authorization: Bearer} header (SDR-002's documented alternative for
 * non-browser clients — the only reason Swagger's "Authorize" button or
 * Postman can exercise protected endpoints without a browser holding the
 * cookie), verifies it, and populates {@code SecurityContext} on success.
 * <p>
 * A missing, malformed, expired, stale-{@code tokenVersion}, locked, or
 * disabled-account token all resolve the same way: {@code SecurityContext}
 * is simply left empty and the chain continues (08-Security-Controls.md
 * §8.1 step 4) — public endpoints (login, refresh, Swagger, health) must
 * still pass through this filter untouched; only the downstream
 * authorization layer, configured in {@code SecurityConfig}, actually
 * rejects an unauthenticated request to a protected route.
 * <p>
 * Deliberately not a {@code @Component} — constructed explicitly inside
 * {@code SecurityConfig} and wired via {@code addFilterBefore}, so Spring
 * Boot's automatic {@code Filter}-bean-to-{@code FilterRegistrationBean}
 * registration (which would otherwise apply it a second time, outside
 * Spring Security's own chain, to every servlet path) never happens.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService customUserDetailsService) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request).ifPresent(this::authenticate);
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parseClaims(token);
            Long userId = Long.valueOf(claims.getSubject());
            UserPrincipal principal = (UserPrincipal) customUserDetailsService.loadUserById(userId);

            if (!jwtService.isTokenVersionCurrent(claims, principal.tokenVersion())) {
                log.debug("Rejected access token: stale tokenVersion for userId={}", userId);
                return;
            }
            if (!principal.isAccountNonLocked() || !principal.isEnabled()) {
                log.debug("Rejected access token: account state no longer permits it, userId={}", userId);
                return;
            }

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected access token: {}", e.getMessage());
        } catch (UsernameNotFoundException e) {
            log.debug("Access token subject no longer resolves to a user: {}", e.getMessage());
        }
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String cookieToken = extractFromCookie(request);
        if (cookieToken != null) {
            return Optional.of(cookieToken);
        }
        return Optional.ofNullable(extractFromHeader(request));
    }

    private String extractFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SecurityConstants.ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String extractFromHeader(HttpServletRequest request) {
        String header = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return header.substring(SecurityConstants.BEARER_PREFIX.length());
        }
        return null;
    }
}
