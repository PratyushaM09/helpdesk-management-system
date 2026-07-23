package com.helpdesk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS policy for the REST API surface ({@code /api/**}), exposed as a
 * {@link CorsConfigurationSource} bean for {@code SecurityConfig} to consume
 * via {@code http.cors(...)} (Phase 2, Milestone 3) — previously a
 * {@code WebMvcConfigurer}, migrated per this class's own prior Javadoc,
 * written specifically for this moment: "When the Security milestone
 * introduces [a SecurityFilterChain], it should consume the same
 * CorsProperties-backed CorsConfigurationSource rather than duplicating this
 * policy." Now that a real {@code SecurityFilterChain} exists, letting the
 * old MVC-level CORS handling run alongside it would be two independent
 * CORS mechanisms applied to the same requests — this migration removes
 * that duplication rather than adding to it.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods());
        configuration.setAllowedHeaders(corsProperties.allowedHeaders());
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        configuration.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
