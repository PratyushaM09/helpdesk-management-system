package com.helpdesk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC-level CORS policy for the future REST API surface ({@code /api/**}).
 * <p>
 * Deliberately configured via {@link WebMvcConfigurer}, not Spring Security's
 * {@code http.cors(...)}, because no {@code SecurityFilterChain} exists yet in
 * this milestone (authentication is out of scope). When the Security milestone
 * introduces one, it should consume the same {@link CorsProperties}-backed
 * {@code org.springframework.web.cors.CorsConfigurationSource} rather than
 * duplicating this policy.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]))
                .allowedMethods(corsProperties.allowedMethods().toArray(new String[0]))
                .allowedHeaders(corsProperties.allowedHeaders().toArray(new String[0]))
                .allowCredentials(corsProperties.allowCredentials())
                .maxAge(corsProperties.maxAge());
    }
}
