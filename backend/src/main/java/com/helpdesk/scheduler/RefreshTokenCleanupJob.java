package com.helpdesk.scheduler;

import com.helpdesk.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Purges long-expired {@code refresh_tokens} rows so the table doesn't grow
 * unbounded — every login/refresh inserts a row that's never deleted
 * otherwise (rotation only marks a row replaced/revoked, it never removes
 * one). A 30-day retention window keeps rows around well past their
 * 7-day {@code expiresAt} — long enough for a forensic/incident-response
 * look-back at a recently-expired session — before they're purged.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now().minus(RETENTION);
        refreshTokenRepository.deleteByExpiresAtBefore(cutoff);
        log.info("Purged refresh tokens that expired before {}", cutoff);
    }
}
