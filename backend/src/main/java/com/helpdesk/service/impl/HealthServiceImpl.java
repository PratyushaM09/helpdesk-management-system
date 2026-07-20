package com.helpdesk.service.impl;

import com.helpdesk.dto.response.HealthResponse;
import com.helpdesk.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthServiceImpl.class);

    /** Matches Spring's own JDBC validation-query timeout convention: seconds, not millis. */
    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 2;
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    private final Environment environment;
    private final DataSource dataSource;
    private final BuildProperties buildProperties;

    public HealthServiceImpl(Environment environment, DataSource dataSource,
                              ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.environment = environment;
        this.dataSource = dataSource;
        // Only populated when built via `mvn package`/`verify` (pom.xml's
        // build-info execution) - null when run unpackaged from an IDE.
        // Optional by design; never a reason for the app to fail to start.
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
    }

    @Override
    public HealthResponse getHealth() {
        String databaseStatus = checkDatabase();
        String overallStatus = STATUS_UP.equals(databaseStatus) ? STATUS_UP : STATUS_DOWN;

        return new HealthResponse(
                overallStatus,
                environment.getProperty("spring.application.name", "helpdesk-management-system"),
                resolveVersion(),
                resolveActiveProfile(),
                Instant.now(),
                System.getProperty("java.version"),
                databaseStatus
        );
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS) ? STATUS_UP : STATUS_DOWN;
        } catch (SQLException ex) {
            // A down dependency is a reportable health fact, not an
            // application error - WARN, no stack trace, never propagated
            // (11-Development-Rules.md §13.2; this is exactly the
            // "expected-but-notable failure" case).
            log.warn("Health check: database connectivity check failed: {}", ex.getMessage());
            return STATUS_DOWN;
        }
    }

    private String resolveVersion() {
        return buildProperties != null
                ? buildProperties.getVersion()
                : "unknown (build with `mvn package` to populate build metadata)";
    }

    private String resolveActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length > 0 ? String.join(",", activeProfiles) : "default";
    }
}
