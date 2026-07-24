package com.helpdesk.controller;

import com.helpdesk.common.ApiResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.dto.response.HealthResponse;
import com.helpdesk.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint purpose (task 5): a minimal, dependency-free-to-call
 * operational status check — "is the application up, and can it reach its
 * database" — for local verification, load balancers, and manual
 * diagnosis. It is intentionally not a business endpoint and carries no
 * authentication requirement (listed in {@code SecurityConfig}'s
 * {@code PUBLIC_PATHS}, alongside the Swagger/OpenAPI docs paths) — the
 * same reasoning Kubernetes liveness/readiness probes and Spring Boot
 * Actuator's own {@code /actuator/health} are built on.
 * <p>
 * Expected response (200, database reachable):
 * <pre>{@code
 * {
 *   "success": true,
 *   "message": "Health check completed",
 *   "data": {
 *     "status": "UP",
 *     "application": "helpdesk-management-system",
 *     "version": "0.0.1-SNAPSHOT",
 *     "activeProfile": "dev",
 *     "timestamp": "2026-07-20T18:40:00Z",
 *     "javaVersion": "24.0.1",
 *     "database": "UP"
 *   },
 *   "timestamp": "2026-07-20T18:40:00Z"
 * }
 * }</pre>
 * When the database check fails, the same shape is returned with
 * {@code status}/{@code database} both {@code "DOWN"} and HTTP
 * {@code 503} instead of {@code 200} — matching the convention Actuator itself uses
 * ({@code management.endpoint.health.status.http-mapping}), so a future
 * migration to it changes nothing about how a caller interprets the
 * response.
 * <p>
 * See {@link HealthService}'s Javadoc for the full future-Actuator-migration note.
 */
@Tag(name = "Health", description = "Operational status of the running application")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(
            summary = "Report application health and readiness",
            description = "Confirms the application started, reports the active profile, application/JVM "
                    + "version, and database connectivity. Never exposes connection strings, credentials, or "
                    + "other configuration detail."
    )
    // Overrides OpenApiConfig's document-level default security requirement
    // for this public endpoint (Phase 2, Milestone 6). Deliberately
    // @SecurityRequirements (empty), not @Operation(security = {}): the
    // latter's attribute default is also {}, so swagger-core can't tell
    // "explicitly cleared" from "left unset" and silently ignores it,
    // leaving the operation incorrectly documented as requiring auth.
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Application and database are both reachable")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Application is running but the database is not reachable")
    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        HealthResponse health = healthService.getHealth();
        HttpStatus httpStatus = "UP".equals(health.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(ApiResponse.success("Health check completed", health));
    }
}
