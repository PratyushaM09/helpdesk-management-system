package com.helpdesk.config;

import com.helpdesk.dto.response.HealthResponse;
import com.helpdesk.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Task 4: logs a single, INFO-level startup summary once the application
 * is fully ready to serve traffic — not a per-request log (that would be
 * {@link HealthService} being called by {@code HealthController} on every
 * health-check request, which is deliberately not logged at INFO to avoid
 * flooding the log with a line per load-balancer probe).
 * <p>
 * Reuses {@link HealthService} rather than duplicating its database-check
 * logic — this listener firing successfully is itself an implicit,
 * zero-extra-code proof that the health service works correctly at boot
 * time, in addition to logging.
 */
@Component
public class ApplicationStartupListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupListener.class);

    private final HealthService healthService;

    public ApplicationStartupListener(HealthService healthService) {
        this.healthService = healthService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupSummary() {
        HealthResponse health = healthService.getHealth();
        log.info("{} v{} ready - profile=[{}], database={}, java={}",
                health.application(), health.version(), health.activeProfile(), health.database(), health.javaVersion());
    }
}
