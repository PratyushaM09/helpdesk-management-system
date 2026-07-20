package com.helpdesk.service;

import com.helpdesk.dto.response.HealthResponse;

/**
 * Reports the running application's operational status (Milestone 6,
 * task 2's readiness verification: started, database connectivity,
 * active profile, JVM version, application version).
 * <p>
 * <b>Future Actuator integration (task 5):</b> this hand-written service
 * exists because Actuator is not yet a dependency of this project. When
 * {@code spring-boot-starter-actuator} is added (per the roadmap already
 * named in 02-Architecture.md §21, item 3 — "Observability stack"), the
 * natural migration is:
 * <ul>
 *   <li>The database check in {@code HealthServiceImpl} becomes a
 *       {@code HealthIndicator} bean (Actuator auto-detects and composes
 *       it into {@code /actuator/health} automatically) — the exact same
 *       {@code connection.isValid(timeout)} technique, just relocated.</li>
 *   <li>{@code /api/v1/health} can either be retired in favor of
 *       {@code /actuator/health} directly, or kept as a stable, versioned,
 *       API-consumer-facing alias — that is a deliberate decision for
 *       whoever does that migration, not decided here.</li>
 *   <li>{@code BuildProperties} (already used below) is the same bean
 *       Actuator's own {@code /actuator/info} endpoint reads — no rework
 *       needed there either.</li>
 * </ul>
 * This interface/implementation split (rather than a static method) is
 * what makes that swap a contained, single-class change: nothing that
 * depends on {@link HealthService} needs to change.
 */
public interface HealthService {

    HealthResponse getHealth();
}
