package com.helpdesk.security;

import io.jsonwebtoken.Claims;

/**
 * Access-token issuance and verification only (ADR-0003, 03-Security.md §3)
 * — the refresh token is a separate, opaque, non-JWT value (SDR-003) and is
 * never handled here. Pure cryptography/claims logic: no repository access,
 * no HTTP access, no {@code SecurityContext} access. Signature and
 * expiration are verified as an inseparable part of {@link #parseClaims}
 * (the JWT library performs both checks while decoding — there is no
 * "verify signature" step distinct from parsing a token successfully);
 * {@code tokenVersion} freshness is a separate check because it needs a
 * caller-supplied current value this class has no way to look up itself.
 */
public interface JwtService {

    /** Issues a new, signed access token for the given principal (07-Security-Architecture.md §3.2 step 6). */
    String generateAccessToken(UserPrincipal principal);

    /**
     * Parses and fully verifies a token's signature and expiry, returning
     * its claims on success.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, has
     *                                       an invalid signature, or has expired
     */
    Claims parseClaims(String token);

    /** Whether the token's {@code tokenVersion} claim still matches the caller-supplied current value. */
    boolean isTokenVersionCurrent(Claims claims, int currentTokenVersion);
}
