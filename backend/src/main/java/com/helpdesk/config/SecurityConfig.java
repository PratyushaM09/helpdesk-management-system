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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

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
 * <b>No method security (</b>{@code @EnableMethodSecurity}<b>) yet</b> — no
 * {@code @PreAuthorize} annotation exists anywhere in this codebase yet;
 * ADR-0004's fine-grained, ownership-aware layer is explicitly a later,
 * separate Authorization milestone ({@code ForbiddenException}'s own
 * Javadoc), not silently pulled forward here.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            ApiConstants.API_BASE_PATH + "/health",
            ApiConstants.API_BASE_PATH + "/auth/login",
            ApiConstants.API_BASE_PATH + "/auth/refresh"
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
                        .requestMatchers(ApiConstants.API_BASE_PATH + "/users/**").hasRole("ADMIN")
                        .requestMatchers(ApiConstants.API_BASE_PATH + "/roles/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(csrfValidationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
