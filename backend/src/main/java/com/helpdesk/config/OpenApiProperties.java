package com.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code app.openapi.*} block — every value shown on the
 * generated documentation's info page is externalized here rather than
 * hardcoded in {@link OpenApiConfig}, so the deployed server URL, and any
 * eventual contact/license change, is a configuration edit, not a code
 * change (11-Development-Rules.md §17's "strongly-typed configuration"
 * convention, same pattern as {@link CorsProperties}).
 * <p>
 * Contact/license values in {@code application.yml} are placeholders —
 * replace them with the real organizational values before a production
 * release; nothing here depends on their content being real.
 */
@ConfigurationProperties(prefix = "app.openapi")
public record OpenApiProperties(
        String title,
        String description,
        String version,
        Contact contact,
        License license,
        Server server
) {

    public record Contact(String name, String email, String url) {
    }

    public record License(String name, String url) {
    }

    public record Server(String url, String description) {
    }
}
