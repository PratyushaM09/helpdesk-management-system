package com.helpdesk.account.service;

/**
 * Cryptographic utilities only — no persistence, no business rule about
 * what a token is *for* or how long it lives. {@code AccountServiceImpl}
 * decides expiry/purpose; this service only generates and hashes opaque
 * values (Milestone 4 design, Change 6). Extracted out of
 * {@code AccountServiceImpl} rather than left as private methods there
 * because it's used identically by both the password-reset flow (this
 * iteration) and the future email-verification flow — a second inline copy
 * would be pure duplication of security-sensitive code.
 * <p>
 * Deliberately not shared with {@code AuthenticationServiceImpl}'s own
 * (identical-algorithm) refresh-token generation: that logic is private to
 * the auth module, and reaching across module boundaries for a few lines of
 * crypto would trade a small amount of duplication for a cross-module
 * coupling that isn't otherwise needed — the same boundary-respecting
 * reasoning already applied when {@code AbstractUserToken} was kept
 * separate from {@code RefreshToken}.
 */
public interface SecureTokenService {

    /** A new random, URL-safe, unguessable token — the raw value handed to the user, never persisted as-is. */
    String generateToken();

    /** The value actually persisted for a given raw token; deterministic, so a re-hash of the same input always matches. */
    String hashToken(String rawToken);
}
