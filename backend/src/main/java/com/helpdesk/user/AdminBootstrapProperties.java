package com.helpdesk.user;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code app.admin-bootstrap.*} — the one-time credentials
 * {@link UserSeeder} uses to create the first {@code ADMIN} account.
 * Secret-shaped per SDR-015: no default in {@code application-prod.yml},
 * environment-variable-sourced everywhere, same contract as
 * {@code app.jwt.secret}/{@code app.jwt.private-key}.
 */
@Validated
@ConfigurationProperties(prefix = "app.admin-bootstrap")
public record AdminBootstrapProperties(
        @NotBlank
        String email,

        @NotBlank
        String password
) {
}
