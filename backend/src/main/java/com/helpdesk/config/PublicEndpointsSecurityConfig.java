package com.helpdesk.config;

import com.helpdesk.constant.ApiConstants;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Not authentication configuration — a narrow, documented exception to
 * Spring Security's default "secure everything" posture, preserved
 * exactly for every other path by {@link #defaultSecurityFilterChain}
 * below. Originally named {@code SwaggerSecurityConfig} (Milestone 5);
 * renamed here because it now also covers the Milestone 6 health
 * endpoint — its actual scope is "public operational endpoints", not
 * Swagger specifically.
 * <p>
 * Permitted unauthenticated:
 * <ul>
 *   <li>{@code /v3/api-docs/**}, {@code /swagger-ui/**}, {@code /swagger-ui.html}
 *       — Milestone 5; see 09-Security-Operations.md §17.6 (disabled
 *       outright in {@code prod}, so this only matters in dev/test).</li>
 *   <li>{@code ApiConstants.API_BASE_PATH + "/health"} — Milestone 6; a
 *       health/readiness check must be reachable by a load balancer or
 *       orchestrator without credentials, the same reasoning Kubernetes
 *       probes and Spring Boot Actuator's own {@code /actuator/health}
 *       are built on. It exposes no sensitive detail (see
 *       {@code HealthResponse}'s Javadoc), so unauthenticated access
 *       carries no meaningful risk.</li>
 * </ul>
 * <p>
 * <b>Two {@code SecurityFilterChain} beans, not one, and not
 * {@code WebSecurityCustomizer#ignoring()}:</b> an earlier version of this
 * class used {@code web.ignoring()}, which is simpler but is explicitly
 * flagged by Spring Security itself at startup as discouraged
 * ("please use permitAll via HttpSecurity#authorizeHttpRequests instead")
 * — {@code ignoring()} bypasses the entire security filter infrastructure
 * for matched paths (not just authorization), which is broader than this
 * narrow case needs. Defining any {@code SecurityFilterChain} bean makes
 * Spring Boot's own auto-configured default one back off entirely
 * ({@code @ConditionalOnDefaultWebSecurity}), so {@link #defaultSecurityFilterChain}
 * exists purely to preserve today's exact prior behavior for every other
 * path — it is a line-for-line replica of
 * {@code SpringBootWebSecurityConfiguration.SecurityFilterChainConfiguration
 * #defaultSecurityFilterChain}, not a new decision. It is deleted, not
 * extended, the moment the real Authentication/Authorization milestone
 * (07-Security-Architecture.md §3–4) introduces the actual JWT-based
 * {@code SecurityFilterChain} — at that point, every path this class
 * permits today either stays public by an explicit, reviewed decision in
 * that new chain, or gains a real requirement; nothing here survives by
 * default.
 */
@Configuration
public class PublicEndpointsSecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            ApiConstants.API_BASE_PATH + "/health"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain publicEndpointsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(PUBLIC_PATHS)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // GET-only, read-only resources — no state-changing action
                // exists on any of these paths for CSRF to protect.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(SecurityProperties.BASIC_AUTH_ORDER)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
        http.formLogin(withDefaults());
        http.httpBasic(withDefaults());
        return http.build();
    }
}
