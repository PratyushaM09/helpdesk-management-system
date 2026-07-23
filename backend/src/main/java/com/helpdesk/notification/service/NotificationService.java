package com.helpdesk.notification.service;

/**
 * The one boundary between this codebase and actually delivering a message
 * to a user. No SMTP/external integration exists yet (Milestone 4 design) —
 * {@code LoggingNotificationServiceImpl} is the only implementation, so
 * every call here currently just logs. Kept as an interface from the start
 * so a real implementation is a drop-in replacement later; no caller
 * (Account service methods) needs to change when that happens.
 * <p>
 * Deliberately receives the raw, unhashed token — this is the one place in
 * the system a raw reset/verification token is allowed to exist outside the
 * request that generated it, since delivering it to the user is the entire
 * point. It must never be logged (see the implementation's own note).
 */
public interface NotificationService {

    void sendPasswordResetEmail(String email, String rawToken);

    void sendEmailVerificationEmail(String email, String rawToken);
}
