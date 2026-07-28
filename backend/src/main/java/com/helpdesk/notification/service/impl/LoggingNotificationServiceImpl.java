package com.helpdesk.notification.service.impl;

import com.helpdesk.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Stub implementation (Milestone 4 design) — logs that a notification would
 * have been sent instead of actually sending one. The raw token is accepted
 * as a parameter (a real implementation needs it to build the email body/
 * link) but is deliberately never included in the log line itself; only the
 * recipient address is, which is not sensitive in the same way a live,
 * unexpired token is.
 * <p>
 * {@code @Profile("!prod")}: {@link com.helpdesk.notification.service.impl.SmtpNotificationServiceImpl}
 * is the real, {@code @Profile("prod")}-only implementation for email
 * verification — dev/test/docker still get this no-op stub, since none of
 * them have real SMTP credentials configured (nor should they need any to
 * run locally).
 */
@Service
@Profile("!prod")
public class LoggingNotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationServiceImpl.class);

    @Override
    public void sendPasswordResetEmail(String email, String rawToken) {
        log.info("Password reset notification would be sent: email={}", email);
    }

    @Override
    public void sendEmailVerificationEmail(String email, String rawToken) {
        log.info("Email verification notification would be sent: email={}", email);
    }
}
