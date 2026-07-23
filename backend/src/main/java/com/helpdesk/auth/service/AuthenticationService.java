package com.helpdesk.auth.service;

import com.helpdesk.auth.dto.request.LoginRequest;
import org.springframework.http.ResponseCookie;

import java.util.List;

/**
 * The Authentication module's public contract (SDR-017) — the orchestration
 * layer that decides lockout policy, session issuance, and rotation.
 * {@code JwtService} only mints/verifies tokens, {@code CookieService} only
 * builds cookies, the repositories only persist — none of them decide *why*;
 * this interface is the one boundary that does.
 */
public interface AuthenticationService {

    /**
     * @throws com.helpdesk.exception.LockedException      the account is within its SDR-005 lockout window
     * @throws com.helpdesk.exception.UnauthorizedException the email/password combination is invalid
     */
    AuthenticationResult login(LoginRequest request);

    /**
     * Rotates a refresh token (SDR-003): the presented value is consumed and
     * a new access/refresh pair is issued. Presenting an already-rotated
     * token revokes its entire family and fails the same way an invalid
     * token does — the caller cannot distinguish reuse-detection from any
     * other invalid-token case, by design.
     *
     * @throws com.helpdesk.exception.UnauthorizedException the token is unknown, expired, revoked, or already rotated
     */
    AuthenticationResult refresh(String rawRefreshToken);

    /**
     * Revokes the single presented refresh token, if it still resolves to a
     * live record — idempotent; an already-invalid/missing token is not an
     * error. Always returns the cleared-cookie set regardless.
     */
    List<ResponseCookie> logout(String rawRefreshToken);
}
