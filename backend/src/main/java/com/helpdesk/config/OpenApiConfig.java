package com.helpdesk.config;

import com.helpdesk.constant.ApiConstants;
import com.helpdesk.security.SecurityConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Root Springdoc/OpenAPI configuration (task 1). Every future
 * {@code @RestController} needs zero configuration of its own to appear
 * here — Springdoc discovers annotated request mappings automatically at
 * startup; this class only supplies the document-level metadata (title,
 * contact, servers, ...) and the reusable building blocks (the {@code v1}
 * group, the cookie auth scheme) future controllers opt into.
 */
@Configuration
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfig {

    /**
     * Name every generated operation's security requirement references,
     * directly or by inheriting the document-level default this class
     * applies below. This describes the application's actual
     * authentication mechanism (Phase 2, Milestone 6) — an HttpOnly,
     * Secure {@code access_token} cookie issued by {@code POST /auth/login}
     * and read by {@code JwtAuthenticationFilter} — replacing an earlier,
     * unused {@code bearerAuth} scheme that documented a classic
     * {@code Authorization: Bearer} header this application has never
     * actually accepted.
     */
    public static final String COOKIE_AUTH_SCHEME_NAME = "cookieAuth";

    @Bean
    public OpenAPI customOpenAPI(OpenApiProperties properties) {
        return new OpenAPI()
                .info(buildInfo(properties))
                .servers(List.of(buildServer(properties.server())))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH_SCHEME_NAME, buildCookieAuthScheme()))
                // A document-level default, not a per-operation opt-in
                // (Phase 2, Milestone 6): most operations require
                // authentication, so every operation inherits this
                // requirement unless it explicitly opts out with
                // @Operation(security = {}) - the handful of genuinely
                // public endpoints (health, login, refresh,
                // forgot/reset-password, verify-email). A new authenticated
                // endpoint therefore documents itself correctly with zero
                // extra annotation; only a new *public* one needs one.
                .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH_SCHEME_NAME));
    }

    /**
     * Task 2's "API grouping (future-ready)" demonstration: every future
     * controller mapped under {@link ApiConstants#API_BASE_PATH} is
     * included in the {@code v1} group with zero changes to this class —
     * proven directly by {@code OpenApiConfigIntegrationTest}, which adds
     * a throwaway test-only controller under that prefix and asserts it
     * appears in the generated document.
     */
    @Bean
    public GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1")
                .displayName("API v1")
                .pathsToMatch(ApiConstants.API_BASE_PATH + "/**")
                .build();
    }

    private Info buildInfo(OpenApiProperties properties) {
        return new Info()
                .title(properties.title())
                .description(properties.description())
                .version(properties.version())
                .contact(buildContact(properties.contact()))
                .license(buildLicense(properties.license()));
    }

    private Contact buildContact(OpenApiProperties.Contact contact) {
        return new Contact()
                .name(contact.name())
                .email(contact.email())
                .url(contact.url());
    }

    private License buildLicense(OpenApiProperties.License license) {
        return new License()
                .name(license.name())
                .url(license.url());
    }

    private Server buildServer(OpenApiProperties.Server server) {
        return new Server()
                .url(server.url())
                .description(server.description());
    }

    /**
     * {@code apiKey}/{@code cookie} is the correct OpenAPI 3 shape for
     * "the value is a bearer credential, but transported as a cookie
     * rather than a header" — there is no dedicated {@code SecurityScheme.Type}
     * for JWT-in-cookie specifically, so {@code APIKEY}+{@code COOKIE} (naming
     * the actual cookie) plus a description spelling out that the value is
     * a JWT is the accurate representation, matching how
     * {@code JwtAuthenticationFilter} actually reads the credential.
     * Swagger UI's "Authorize" button lets a caller paste a raw JWT value
     * here for its own "Try it out" requests, but cannot itself perform a
     * real login — the caller still needs to authenticate via
     * {@code POST /auth/login} in a real browser/client first and copy the
     * resulting cookie value, same limitation any cookie-based scheme has
     * in Swagger UI.
     */
    private SecurityScheme buildCookieAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name(SecurityConstants.ACCESS_TOKEN_COOKIE)
                .description("JWT access token, issued as an HttpOnly, Secure cookie by POST " + ApiConstants.API_BASE_PATH
                        + "/auth/login (07-Security-Architecture.md §3/§5.8). Not a header credential: this "
                        + "application never accepts an Authorization: Bearer header.");
    }
}
