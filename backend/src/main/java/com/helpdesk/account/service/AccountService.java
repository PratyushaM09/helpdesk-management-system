package com.helpdesk.account.service;

import com.helpdesk.account.dto.request.ChangePasswordRequest;
import com.helpdesk.account.dto.request.ForgotPasswordRequest;
import com.helpdesk.account.dto.request.ResetPasswordRequest;
import com.helpdesk.account.dto.request.UpdateProfileRequest;
import com.helpdesk.account.dto.request.VerifyEmailRequest;
import com.helpdesk.account.dto.response.AccountProfileResponse;

/**
 * The Account module's public contract, expressed only in DTOs — never an
 * entity or repository type (11-Development-Rules.md §8.5), the same
 * convention {@code UserService} follows.
 * <p>
 * Most methods here act on "the caller" — the target user is resolved from
 * the current {@code SecurityContext}, never a path/request parameter, so
 * there is no way to call them on behalf of another user.
 * {@code forgotPassword}/{@code resetPassword}/{@code verifyEmail} are the
 * deliberate exception: they run before authentication exists or (for
 * verification) target the account a possessed token was issued to, so they
 * identify the target user by email or token rather than by session.
 * {@code activateUser}/{@code deactivateUser} are the other exception —
 * admin operations, explicitly targeting another user's id.
 * <p>
 * This is the final iteration of the Account service (Milestone 4 design):
 * profile self-service, password change/reset, email verification, and
 * admin activation/deactivation are all implemented here.
 */
public interface AccountService {

    AccountProfileResponse getCurrentUserProfile();

    AccountProfileResponse updateProfile(UpdateProfileRequest request);

    /**
     * Verifies {@code request.currentPassword()}, then replaces it with
     * {@code request.newPassword()} — and, because a password change is a
     * security-invalidating event, also bumps {@code tokenVersion} and
     * revokes every refresh token this user holds. Returns nothing: no new
     * session is issued, so the caller is logged out everywhere and must
     * authenticate again.
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * Always succeeds, with an identical outcome, whether or not
     * {@code request.email()} belongs to an account — whether it does is
     * never observable from the response (Milestone 4 design). If it does,
     * issues a single-use, 30-minute password reset token and dispatches it
     * via {@code NotificationService}.
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Redeems a password reset token issued by {@link #forgotPassword}.
     * Every way the token can fail to be redeemable — missing, expired,
     * already used — is reported identically via
     * {@code InvalidTokenException}, so a caller can never learn which
     * condition occurred. On success, behaves like {@link #changePassword}:
     * {@code tokenVersion} is bumped and every refresh token is revoked, so
     * the account is logged out everywhere and must authenticate again.
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Redeems an email verification token. Idempotent: redeeming a token for
     * an already-verified account is not an error, does not overwrite the
     * original verification timestamp, and still marks the token used.
     * Every way the token can fail to be redeemable shares
     * {@code InvalidTokenException}, same as {@link #resetPassword}.
     */
    void verifyEmail(VerifyEmailRequest request);

    /**
     * Issues a fresh verification token for the caller, if they aren't
     * already verified — a no-op (not an error) when they are. Always uses
     * the authenticated caller's own email; there is no way to request a
     * verification email for anyone else.
     */
    void resendVerification();

    /**
     * Admin operation: restores an account's lifecycle status to
     * {@code ACTIVE}. A no-op if already {@code ACTIVE}. Touches nothing
     * else — see the implementation's note on why activation and email
     * verification stay separate.
     */
    void activateUser(Long userId);

    /**
     * Admin operation: sets an account's lifecycle status to
     * {@code DEACTIVATED} and forces it out of every current session — bumps
     * {@code tokenVersion} and revokes every refresh token, the same
     * security-invalidation {@link #changePassword} performs on itself. A
     * no-op if already {@code DEACTIVATED}.
     */
    void deactivateUser(Long userId);
}
