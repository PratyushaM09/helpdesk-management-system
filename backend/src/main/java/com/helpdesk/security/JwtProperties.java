package com.helpdesk.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Binds the {@code app.jwt.*} block — profile-aware by design, not by code
 * branching: {@code dev}/{@code test} supply {@code algorithm: HS512} +
 * {@code secret}, {@code prod} supplies {@code algorithm: RS256} +
 * {@code privateKey}/{@code publicKey} (ADR-0003, 03-Security.md §3). The
 * class itself has one shape for every profile; only the bound
 * <em>values</em> differ, same pattern as {@link com.helpdesk.config.CorsProperties}.
 * <p>
 * {@code secret}/{@code privateKey}/{@code publicKey} are deliberately
 * unvalidated here — which of the three is actually required depends on
 * {@code algorithm}, and resolving that conditional is {@code JwtService}'s
 * job when it builds the actual signing key (it fails fast at construction
 * if the material its configured algorithm needs is missing), not this
 * class's, which stays a dumb data holder per its own scope.
 * <p>
 * Activated via {@code @ConfigurationPropertiesScan} on
 * {@link com.helpdesk.HelpDeskManagementSystemApplication}, not
 * {@code @Component} — {@code @Component} was tried first and is actively
 * wrong for a {@code @ConfigurationProperties} <em>record</em>: Spring's
 * ordinary bean-autowiring machinery claims the single constructor before
 * the properties-binding machinery gets to, and tries to inject each
 * component (e.g. {@code algorithm}) as a bean rather than bind it from
 * YAML — confirmed by an actual context-startup failure, not a hypothetical.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank
        @Pattern(regexp = "HS512|RS256", message = "app.jwt.algorithm must be HS512 or RS256")
        String algorithm,

        String secret,

        String privateKey,

        String publicKey,

        @NotNull
        Duration accessTokenTtl,

        @NotNull
        Duration refreshTokenTtl,

        @NotBlank
        String issuer
) {
}
