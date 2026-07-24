package com.helpdesk.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.security.SecurityConstants;
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
 * {@code SecurityConfig}'s permitted-paths chain and {@link OpenApiConfig}'s
 * bean wiring compose correctly.
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
    void openApiJson_shouldExposeCookieAuthSchemeAsTheGlobalDefault_exceptOnPublicEndpoints() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        JsonNode root = new ObjectMapper().readTree(response.getBody());

        JsonNode cookieScheme = root.at("/components/securitySchemes/" + OpenApiConfig.COOKIE_AUTH_SCHEME_NAME);
        assertFalse(cookieScheme.isMissingNode(), "cookieAuth scheme must be registered (Phase 2, Milestone 6)");
        assertEquals("apiKey", cookieScheme.get("type").asText());
        assertEquals("cookie", cookieScheme.get("in").asText());
        assertEquals(SecurityConstants.ACCESS_TOKEN_COOKIE, cookieScheme.get("name").asText());

        // Document-level default: every operation requires cookieAuth unless it opts out.
        JsonNode globalSecurity = root.path("security");
        assertTrue(globalSecurity.isArray() && !globalSecurity.isEmpty());
        assertTrue(globalSecurity.get(0).has(OpenApiConfig.COOKIE_AUTH_SCHEME_NAME));

        // A representative public endpoint (@Operation(security = {})) opts back out.
        String healthPath = ApiConstants.API_BASE_PATH + "/health";
        JsonNode healthSecurity = root.at("/paths/" + healthPath.replace("/", "~1") + "/get/security");
        assertTrue(healthSecurity.isArray() && healthSecurity.isEmpty(),
                "public /health operation must declare an empty security array");
    }

    @Test
    void swaggerUi_shouldLoad_unauthenticated() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui.html", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String body = response.getBody();
        assertTrue(body != null && body.toLowerCase().contains("swagger"));
    }
}
