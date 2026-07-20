package com.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code app.cors.*} block (application.yml / application-{profile}.yml)
 * so CORS policy is environment-overridable without a code change — see
 * CorsConfig for how this is applied.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        long maxAge
) {
}
