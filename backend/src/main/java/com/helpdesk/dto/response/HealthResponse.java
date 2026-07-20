package com.helpdesk.dto.response;

import java.time.Instant;

/**
 * The payload {@code GET /api/v1/health} returns, wrapped in the project's
 * standard {@link com.helpdesk.common.ApiResponse} envelope.
 * <p>
 * Deliberately excludes anything sensitive (task 1): no datasource URL,
 * no credentials, no CORS origins, no internal hostnames — {@code database}
 * is a plain {@code UP}/{@code DOWN} status, nothing more.
 *
 * @param status         {@code UP} or {@code DOWN} — the same vocabulary
 *                        Spring Boot Actuator's own {@code /actuator/health}
 *                        uses, deliberately, for a low-friction future
 *                        migration (see {@link com.helpdesk.service.HealthService}).
 * @param application    {@code spring.application.name}
 * @param version        the built artifact version (from {@code BuildProperties},
 *                        populated by the Maven {@code build-info} goal), or a
 *                        clearly-labeled fallback when running unpackaged
 * @param activeProfile  the Spring profile(s) actually active, comma-joined
 * @param timestamp      server time the check was performed
 * @param javaVersion    the JVM's own reported version ({@code java.version})
 * @param database       {@code UP} if a JDBC connection was validated
 *                        within the timeout, {@code DOWN} otherwise — never
 *                        throws, a failed check is reported, not propagated
 */
public record HealthResponse(
        String status,
        String application,
        String version,
        String activeProfile,
        Instant timestamp,
        String javaVersion,
        String database
) {
}
