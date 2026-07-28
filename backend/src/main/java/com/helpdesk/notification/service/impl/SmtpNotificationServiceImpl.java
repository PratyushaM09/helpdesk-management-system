package com.helpdesk.notification.service.impl;

import com.helpdesk.config.FrontendProperties;
import com.helpdesk.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * Real email delivery for account verification, active only in
 * {@code prod} (application-prod.yml supplies the {@code spring.mail.*}
 * SMTP credentials this needs; dev/test/docker keep
 * {@link LoggingNotificationServiceImpl}'s no-op stub instead, since none of
 * them have real credentials configured). {@code JavaMailSender} is
 * auto-configured by {@code spring-boot-starter-mail} purely from those
 * properties — no separate bean definition needed here.
 * <p>
 * {@code sendPasswordResetEmail} deliberately still only logs, matching
 * {@link LoggingNotificationServiceImpl}'s behavior exactly — the
 * forgot/reset-password frontend flow is still a UI placeholder (unlike
 * email verification, which this class completes end-to-end), so a real
 * reset email would have nowhere for its link to go. Revisit once that
 * frontend flow is real.
 */
@Service
@Profile("prod")
public class SmtpNotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificationServiceImpl.class);

    private final JavaMailSender mailSender;
    private final FrontendProperties frontendProperties;

    public SmtpNotificationServiceImpl(JavaMailSender mailSender, FrontendProperties frontendProperties) {
        this.mailSender = mailSender;
        this.frontendProperties = frontendProperties;
    }

    @Override
    public void sendPasswordResetEmail(String email, String rawToken) {
        log.info("Password reset notification would be sent: email={}", email);
    }

    @Override
    public void sendEmailVerificationEmail(String email, String rawToken) {
        String link = frontendProperties.baseUrl() + "/verify-email.html?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Verify your HelpDesk account");
        message.setText(
                "Welcome to HelpDesk!\n\n"
                        + "Confirm your email address to activate your account:\n"
                        + link + "\n\n"
                        + "This link expires in 24 hours. If you didn't request this account, ignore this email.");

        mailSender.send(message);
        log.info("Email verification notification sent: email={}", email);
    }
}
