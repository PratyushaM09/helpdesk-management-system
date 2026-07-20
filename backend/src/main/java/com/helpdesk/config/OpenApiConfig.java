package com.helpdesk.config;

import com.helpdesk.constant.ApiConstants;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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
 * group, the bearer auth scheme) future controllers opt into.
 */
@Configuration
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfig {

    /**
     * Name future {@code @SecurityRequirement(name = BEARER_AUTH_SCHEME)}
     * annotations reference once JWT authentication exists (task 4).
     */
    public static final String BEARER_AUTH_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI(OpenApiProperties properties) {
        return new OpenAPI()
                .info(buildInfo(properties))
                .servers(List.of(buildServer(properties.server())))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME_NAME, buildBearerAuthScheme()));
        // Deliberately no .addSecurityItem(...) here - see buildBearerAuthScheme() Javadoc.
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
     * Task 4: prepares — does not implement — JWT Bearer authentication.
     * Registering the scheme makes Swagger UI's "Authorize" button appear
     * and lets a future controller declare
     * {@code @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME_NAME)}
     * on its own operations with no change here. A global
     * {@code OpenAPI.addSecurityItem(...)} is deliberately NOT added: that
     * would mark every current and future operation as requiring auth by
     * default, which is a real authentication-shaped decision this
     * milestone is explicitly not authorized to make
     * (07-Security-Architecture.md §3 owns that decision). Defining the
     * scheme is additive infrastructure; applying it globally is not.
     */
    private SecurityScheme buildBearerAuthScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer token, once authentication is implemented (07-Security-Architecture.md §3). "
                        + "Not enforced on any operation yet.");
    }
}
