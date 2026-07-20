package com.helpdesk.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one genuine end-to-end test in this milestone — a real embedded
 * server, real unauthenticated HTTP calls, asserting exactly what task 6
 * requires ("Swagger UI loads", "OpenAPI JSON loads") rather than assuming
 * {@link PublicEndpointsSecurityConfig}'s permitted-paths chain and
 * {@link OpenApiConfig}'s bean wiring compose correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocsIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiJson_shouldLoad_unauthenticated_withConfiguredInfo() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode root = new ObjectMapper().readTree(response.getBody());
        assertEquals("HelpDesk Management System API", root.at("/info/title").asText());
        assertEquals("1.0.0", root.at("/info/version").asText());
        assertFalse(root.at("/info/contact/email").isMissingNode());
    }

    @Test
    void openApiJson_shouldExposeThePreparedBearerAuthScheme_butApplyItToNothingYet() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        JsonNode root = new ObjectMapper().readTree(response.getBody());

        JsonNode bearerScheme = root.at("/components/securitySchemes/" + OpenApiConfig.BEARER_AUTH_SCHEME_NAME);
        assertFalse(bearerScheme.isMissingNode(), "bearerAuth scheme must be registered (task 4)");
        assertEquals("http", bearerScheme.get("type").asText());
        assertEquals("bearer", bearerScheme.get("scheme").asText());
        assertEquals("JWT", bearerScheme.get("bearerFormat").asText());

        // Not implemented yet - no global security requirement should be forcing auth on anything.
        assertTrue(root.path("security").isMissingNode() || root.path("security").isEmpty());
    }

    @Test
    void swaggerUi_shouldLoad_unauthenticated() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui.html", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body != null && body.toLowerCase().contains("swagger"));
    }
}
