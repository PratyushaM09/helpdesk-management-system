package com.helpdesk.config;

import com.helpdesk.constant.ApiConstants;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test — no Spring context. Proves the milestone's central claim
 * at the configuration level: the {@code v1} group is wired to match
 * {@code ApiConstants.API_BASE_PATH + "/**"}, so any future controller
 * mapped under {@code /api/v1/...} is included automatically. See
 * {@link OpenApiDocsIntegrationTest} for the real-HTTP-call verification
 * that the document is actually servable and shaped correctly.
 */
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void v1ApiGroup_shouldMatchEveryFutureApiV1Path() {
        GroupedOpenApi group = config.v1Api();

        assertEquals("v1", group.getGroup());
        assertTrue(group.getPathsToMatch().contains(ApiConstants.API_BASE_PATH + "/**"));
    }
}
