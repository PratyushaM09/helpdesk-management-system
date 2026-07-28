package com.helpdesk.auth.service;

import com.helpdesk.auth.dto.request.LoginRequest;
import com.helpdesk.user.entity.User;
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

    /**
     * Issues a fresh CSRF double-submit cookie, unconditionally — cheap and
     * stateless (nothing is persisted; the value is a pure comparison
     * token), so callers rotate it freely rather than conditionally reusing
     * whatever cookie may already be present. Exists for a frontend on a
     * different subdomain than the API: such a frontend cannot read the
     * existing {@code csrf_token} cookie via {@code document.cookie} at all
     * (cookie visibility to JS is scoped to the setting origin), so the
     * caller (a public endpoint, {@code /auth/csrf-token}) hands the value
     * back in the response body instead, letting that frontend cache it in
     * memory and echo it as {@code X-CSRF-Token} on later requests.
     */
    ResponseCookie issueCsrfCookie();

    /**
     * Revokes every currently-live refresh token issued to {@code user},
     * across every session/device/family — the "log out everywhere"
     * primitive security-invalidating events need (password change, password
     * reset; Milestone 4 design, Change 5). Distinct from {@link #logout},
     * which only revokes the single presented token, and from the
     * reuse-detection revoke {@link #refresh} triggers internally, which is
     * scoped to one token family rather than every token a user has.
     * <p>
     * Callers outside this module reach refresh-token revocation only
     * through this method — {@code RefreshTokenRepository} stays internal to
     * the auth module, never injected elsewhere.
     */
    void revokeAllRefreshTokensForUser(User user);
}
