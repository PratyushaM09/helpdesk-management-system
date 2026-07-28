package com.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.frontend} block — the one place the backend needs to
 * know where the frontend actually lives, distinct from {@link CorsProperties}'
 * {@code allowed-origins} (a list, since CORS may need to permit more than
 * one origin; this is always exactly one canonical URL to build a link
 * against). Currently consumed only by the notification module, to build
 * the emailed verification link ({@code baseUrl + "/verify-email.html?token=..."}).
 * <p>
 * Activated via {@code @ConfigurationPropertiesScan} on
 * {@link com.helpdesk.HelpDeskManagementSystemApplication} (same path as
 * {@code JwtProperties}), not a dedicated {@code *Config} class — this is a
 * single field with nothing else to configure alongside it.
 */
@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {
}
