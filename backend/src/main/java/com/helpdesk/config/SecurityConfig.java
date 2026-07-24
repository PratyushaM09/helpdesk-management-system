package com.helpdesk.config;

import com.helpdesk.constant.ApiConstants;
import com.helpdesk.security.CsrfValidationFilter;
import com.helpdesk.security.CustomUserDetailsService;
import com.helpdesk.security.JwtAuthenticationFilter;
import com.helpdesk.security.JwtService;
import com.helpdesk.security.RestAccessDeniedHandler;
import com.helpdesk.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.time.Duration;

/**
 * The real, JWT-based {@code SecurityFilterChain} (07-Security-Architecture.md
 * §3-4) — replaces {@code PublicEndpointsSecurityConfig} entirely, per that
 * class's own Javadoc: "deleted, not extended... every path this class
 * permits today either stays public by an explicit, reviewed decision in
 * that new chain, or gains a real requirement." See the conversation's
 * filter-chain diagram for the full ordering rationale.
 * <p>
 * <b>Stateless sessions</b> (ADR-0003): no {@code HttpSession} is ever
 * created or consulted for authentication state — every request
 * independently re-authenticates via {@link JwtAuthenticationFilter}.
 * <p>
 * <b>{@code httpBasic}/{@code formLogin} explicitly disabled</b> — a
 * deliberate removal, not an omission. Leaving either enabled would let a
 * client authenticate directly against {@code AuthenticationManager} with a
 * raw {@code Authorization: Basic} header on every request, completely
 * bypassing the JWT/cookie/rotation/lockout design ADR-0003 exists to
 * enforce — the explicit {@code .disable()} calls make that removal
 * reviewable rather than a silent side effect of "we just didn't configure
 * it."
 * <p>
 * <b>Spring's own {@code .csrf(...)} is disabled</b> — {@link CsrfValidationFilter}
 * is this project's own double-submit implementation (SDR-007), already
 * integrated with the CSRF cookie {@code CookieService} issues at login;
 * running Spring's built-in {@code CsrfFilter} alongside it would be two
 * independent, conflicting CSRF mechanisms.
 * <p>
 * <b>Method security (</b>{@code @EnableMethodSecurity}<b>) enabled</b>
 * (Phase 2, Milestone 5) — role authorization is now decided at the
 * Controller-method level via {@code @PreAuthorize}
 * ({@code UserController}/{@code RoleController}/{@code AccountController}),
 * not by URL pattern here. This class therefore only distinguishes "public"
 * from "must be authenticated"; which *role* an authenticated caller needs
 * is each method's own declared requirement, not a fact this filter chain
 * tracks.
 * <p>
 * The blanket {@code .anyRequest().authenticated()} below is still load-
 * bearing, not a leftover: it's what still rejects a genuinely
 * unauthenticated caller with 401 before the request ever reaches a
 * {@code @PreAuthorize}-guarded method. A {@code @PreAuthorize} denial
 * itself is a different mechanism entirely from the URL-matcher denials
 * this class used to produce — it throws
 * {@code org.springframework.security.authorization.AuthorizationDeniedException}
 * from *inside* {@code DispatcherServlet}'s dispatch (the method-security
 * AOP interceptor wraps the controller method call itself), so it never
 * reaches {@code ExceptionTranslationFilter}/{@link #restAccessDeniedHandler}
 * the way a request-matcher denial does. {@code GlobalExceptionHandler}
 * has its own handler for that exact exception type, built to return the
 * byte-for-byte identical 403 body {@link #restAccessDeniedHandler} would
 * — so the two mechanisms remain indistinguishable to a client, but they
 * are genuinely two separate code paths, not one.
 * <p>
 * <b>Security headers</b> (Phase 2, Milestone 6): {@code X-Content-Type-Options},
 * {@code X-Frame-Options: DENY}, {@code Referrer-Policy},
 * {@code Permissions-Policy}, {@code Content-Security-Policy}, and
 * {@code Strict-Transport-Security} are all configured explicitly below
 * rather than left to Spring Security's (partial, version-dependent)
 * defaults — production-quality intent should be visible in code, not
 * inferred from what a framework happens to default to. The CSP is
 * deliberately permissive enough for Springdoc's bundled Swagger UI to
 * keep working (see {@link #CONTENT_SECURITY_POLICY}'s own Javadoc); HSTS
 * is safe to configure unconditionally because its header writer only
 * ever emits the header on an already-HTTPS request.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 1 year — a conventional, widely-used HSTS max-age (e.g. the value the HSTS preload list requires). */
    private static final long HSTS_MAX_AGE_SECONDS = Duration.ofDays(365).toSeconds();

    /**
     * A REST API serves no HTML of its own, so {@code default-src 'self'}
     * is safe everywhere except the one page this filter chain also
     * serves: Springdoc's bundled Swagger UI, whose stock
     * {@code swagger-ui.html} relies on an inline initializer
     * {@code <script>} and inline {@code <style>} — hence
     * {@code 'unsafe-inline'} on {@code script-src}/{@code style-src}
     * rather than a stricter nonce-based policy (which would require
     * customizing Springdoc's template, out of scope for this milestone).
     * {@code frame-ancestors 'none'} is the CSP-level equivalent of
     * {@code X-Frame-Options: DENY} below — kept as a explicit belt-and-
     * braces pair since browser support for the two headers doesn't fully
     * overlap.
     */
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
                    + "script-src 'self' 'unsafe-inline'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; "
                    + "font-src 'self' data:; "
                    + "connect-src 'self'; "
                    + "frame-ancestors 'none'";

    /**
     * No {@code HeadersConfigurer.permissionsPolicy(...)} DSL method exists
     * usably here — it was deprecated for removal in Spring Security 6.4,
     * the version this project is already on, and its replacement doesn't
     * chain back to {@code HeadersConfigurer} the way every other header
     * customizer does. {@code addHeaderWriter(new StaticHeadersWriter(...))}
     * is the stable, general-purpose escape hatch for exactly this case —
     * a header Spring Security's typed DSL doesn't (yet, cleanly) cover.
     */
    private static final StaticHeadersWriter PERMISSIONS_POLICY_HEADER_WRITER = new StaticHeadersWriter(
            "Permissions-Policy", "geolocation=(), microphone=(), camera=(), payment=(), usb=()");

    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            ApiConstants.API_BASE_PATH + "/health",
            ApiConstants.API_BASE_PATH + "/auth/login",
            ApiConstants.API_BASE_PATH + "/auth/refresh",
            // Unauthenticated by necessity (Milestone 4): the caller has no
            // session yet, or is presenting a possessed token instead of one.
            // resend-verification is deliberately NOT here - it stays
            // authenticated (@PreAuthorize("isAuthenticated()") on
            // AccountController.resendVerification).
            ApiConstants.API_BASE_PATH + "/account/forgot-password",
            ApiConstants.API_BASE_PATH + "/account/reset-password",
            ApiConstants.API_BASE_PATH + "/account/verify-email"
    };

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    public SecurityConfig(JwtService jwtService,
                           CustomUserDetailsService customUserDetailsService,
                           CorsConfigurationSource corsConfigurationSource,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           RestAccessDeniedHandler restAccessDeniedHandler) {
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
        this.corsConfigurationSource = corsConfigurationSource;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
        CsrfValidationFilter csrfValidationFilter = new CsrfValidationFilter(restAccessDeniedHandler);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(PERMISSIONS_POLICY_HEADER_WRITER)
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // HttpStrictTransportSecurityHeaderWriter only ever writes this
                        // header on an already-HTTPS request (checks request.isSecure()
                        // internally) - configuring it here is safe under plain HTTP
                        // local/dev traffic; it simply never emits the header there.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfValidationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
