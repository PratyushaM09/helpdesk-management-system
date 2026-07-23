package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @ConfigurationPropertiesScan} activates every
 * {@code @ConfigurationProperties} class under this package tree (currently
 * only {@code com.helpdesk.security.JwtProperties}) without needing
 * {@code @Component} on each one — required specifically because
 * {@code @Component} does not correctly constructor-bind a
 * {@code @ConfigurationProperties} <em>record</em> (Spring's normal
 * bean-autowiring machinery claims the constructor instead of the
 * properties-binding machinery, and tries to inject each field as a bean).
 * {@code CorsProperties}/{@code OpenApiProperties} instead use
 * {@code @EnableConfigurationProperties} on their own {@code *Config}
 * class — an equally valid, pre-existing alternative this project already
 * uses; both are legitimate Spring Boot activation paths, applied here
 * where creating a class solely to hold one {@code @EnableConfigurationProperties}
 * annotation would be more ceremony than this single class needs.
 * <p>
 * {@code @EnableScheduling} activates {@code RefreshTokenCleanupJob}'s
 * {@code @Scheduled} method (Phase 2, Milestone 3) — without it, the
 * annotation is silently inert and the job never runs.
 */
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class HelpDeskManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpDeskManagementSystemApplication.class, args);
    }

}
