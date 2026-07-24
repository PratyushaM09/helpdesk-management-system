package com.helpdesk.config;

import com.helpdesk.constant.ApiConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real embedded server, real HTTP call — proves {@code SecurityConfig}'s
 * {@code .headers(...)} block actually reaches the response, not just that
 * it compiles. Uses the public {@code /health} endpoint so no login flow is
 * needed; header behavior doesn't depend on authentication state.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHeadersIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void response_shouldIncludeConfiguredSecurityHeaders() {
        ResponseEntity<String> response = restTemplate.getForEntity(ApiConstants.API_BASE_PATH + "/health", String.class);
        HttpHeaders headers = response.getHeaders();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", headers.getFirst("Referrer-Policy"));
        assertEquals("geolocation=(), microphone=(), camera=(), payment=(), usb=()", headers.getFirst("Permissions-Policy"));

        String csp = headers.getFirst("Content-Security-Policy");
        assertTrue(csp != null && csp.contains("default-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'none'"));
    }

    /**
     * {@code HstsHeaderWriter} only ever writes {@code Strict-Transport-Security}
     * on an already-HTTPS request ({@code request.isSecure()}) — this test
     * runs over plain HTTP (the embedded test server has no TLS), so its
     * absence here is the expected, correct behavior, not a gap. Proves the
     * "safe to configure unconditionally" claim in {@code SecurityConfig}'s
     * own Javadoc rather than just asserting it in a comment.
     */
    @Test
    void response_shouldOmitHstsHeader_overPlainHttp() {
        ResponseEntity<String> response = restTemplate.getForEntity(ApiConstants.API_BASE_PATH + "/health", String.class);

        assertNull(response.getHeaders().getFirst("Strict-Transport-Security"));
    }
}
