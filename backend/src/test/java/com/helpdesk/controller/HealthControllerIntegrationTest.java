package com.helpdesk.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real embedded server, real unauthenticated HTTP call against the
 * "test" profile's H2 database (per task 6, "actually execute... do not
 * assume success") — proves the whole chain end-to-end:
 * {@code SecurityConfig}'s permitted path,
 * {@link com.helpdesk.service.impl.HealthServiceImpl}'s live database
 * check, and {@link com.helpdesk.common.ApiResponse}'s envelope, all
 * wired together correctly. Uses H2, not the real dev MySQL database, so
 * this test (and the whole suite) never depends on a local MySQL install
 * or DB_PASSWORD being set - see application-test.yml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void health_shouldBeReachable_unauthenticated_andReportUp_againstTheTestDatabase() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        JsonNode root = new ObjectMapper().readTree(response.getBody());
        assertTrue(root.get("success").asBoolean());
        assertEquals("UP", root.at("/data/status").asText());
        assertEquals("UP", root.at("/data/database").asText());
        assertEquals("test", root.at("/data/activeProfile").asText());
        assertEquals("helpdesk-management-system", root.at("/data/application").asText());
        assertFalse(root.at("/data/version").isMissingNode());
        assertFalse(root.at("/data/javaVersion").isMissingNode());
        assertFalse(root.at("/data/timestamp").isMissingNode());
    }

    @Test
    void health_response_shouldNeverExposeConnectionDetails() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);
        String body = response.getBody();

        assertTrue(body != null && !body.toLowerCase().contains("jdbc:"));
        assertTrue(body != null && !body.toLowerCase().contains("password"));
    }
}
