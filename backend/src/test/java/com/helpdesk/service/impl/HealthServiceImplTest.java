package com.helpdesk.service.impl;

import com.helpdesk.dto.response.HealthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test — no Spring context. DataSource/Connection/Environment
 * are mocked at the boundary (per 11-Development-Rules.md §16.1), so both
 * the UP and DOWN database paths are exercised deterministically —
 * something the real dev database being reachable can't prove on its own
 * (see {@code HealthControllerIntegrationTest} for that live-DB proof).
 */
class HealthServiceImplTest {

    private Environment environment;
    private DataSource dataSource;
    private Connection connection;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        when(environment.getProperty("spring.application.name", "helpdesk-management-system"))
                .thenReturn("helpdesk-management-system");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
    }

    @Test
    void shouldReportUp_whenDatabaseConnectionIsValid() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        HealthServiceImpl service = new HealthServiceImpl(environment, dataSource, noBuildProperties());

        HealthResponse health = service.getHealth();

        assertEquals("UP", health.status());
        assertEquals("UP", health.database());
        assertEquals("dev", health.activeProfile());
        assertEquals("helpdesk-management-system", health.application());
        assertNotNull(health.timestamp());
        assertNotNull(health.javaVersion());
    }

    @Test
    void shouldReportDown_whenConnectionIsNotValid() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(false);
        HealthServiceImpl service = new HealthServiceImpl(environment, dataSource, noBuildProperties());

        HealthResponse health = service.getHealth();

        assertEquals("DOWN", health.status());
        assertEquals("DOWN", health.database());
    }

    @Test
    void shouldReportDown_whenGettingConnectionThrows_andNeverPropagateTheException() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        HealthServiceImpl service = new HealthServiceImpl(environment, dataSource, noBuildProperties());

        HealthResponse health = service.getHealth();

        assertEquals("DOWN", health.status());
        assertEquals("DOWN", health.database());
    }

    @Test
    void shouldFallBackGracefully_whenBuildPropertiesIsUnavailable() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        HealthServiceImpl service = new HealthServiceImpl(environment, dataSource, noBuildProperties());

        HealthResponse health = service.getHealth();

        assertNotNull(health.version());
    }

    @Test
    void shouldReportRealVersion_whenBuildPropertiesIsAvailable() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        Properties props = new Properties();
        props.setProperty("version", "1.2.3");
        BuildProperties buildProperties = new BuildProperties(props);
        HealthServiceImpl service = new HealthServiceImpl(environment, dataSource, withBuildProperties(buildProperties));

        HealthResponse health = service.getHealth();

        assertEquals("1.2.3", health.version());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<BuildProperties> noBuildProperties() {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<BuildProperties> withBuildProperties(BuildProperties buildProperties) {
        ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(buildProperties);
        return provider;
    }
}
