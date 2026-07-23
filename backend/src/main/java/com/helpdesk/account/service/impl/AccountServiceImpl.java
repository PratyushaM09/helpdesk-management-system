package com.helpdesk.account.service.impl;

import com.helpdesk.account.dto.request.ChangePasswordRequest;
import com.helpdesk.account.dto.request.ForgotPasswordRequest;
import com.helpdesk.account.dto.request.ResetPasswordRequest;
import com.helpdesk.account.dto.request.UpdateProfileRequest;
import com.helpdesk.account.dto.request.VerifyEmailRequest;
import com.helpdesk.account.dto.response.AccountProfileResponse;
import com.helpdesk.account.entity.EmailVerificationToken;
import com.helpdesk.account.entity.PasswordResetToken;
import com.helpdesk.account.mapper.AccountMapper;
import com.helpdesk.account.repository.EmailVerificationTokenRepository;
import com.helpdesk.account.repository.PasswordResetTokenRepository;
import com.helpdesk.account.service.AccountService;
import com.helpdesk.account.service.SecureTokenService;
import com.helpdesk.auth.service.AuthenticationService;
import com.helpdesk.exception.BadRequestException;
import com.helpdesk.exception.InvalidTokenException;
import com.helpdesk.exception.ResourceNotFoundException;
import com.helpdesk.notification.service.NotificationService;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.user.entity.User;
import com.helpdesk.user.entity.UserStatus;
import com.helpdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration EMAIL_VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    /** Shared verbatim by {@link #resetPassword} and {@link #verifyEmail} — neither reveals which failure condition occurred. */
    private static final String INVALID_TOKEN_MESSAGE = "This reset link is invalid or has expired.";

    private final UserRepository userRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationService authenticationService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final NotificationService notificationService;
    private final SecureTokenService secureTokenService;

    public AccountServiceImpl(UserRepository userRepository, AccountMapper accountMapper,
                               PasswordEncoder passwordEncoder, AuthenticationService authenticationService,
                               PasswordResetTokenRepository passwordResetTokenRepository,
                               EmailVerificationTokenRepository emailVerificationTokenRepository,
                               NotificationService notificationService, SecureTokenService secureTokenService) {
        this.userRepository = userRepository;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationService = authenticationService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.notificationService = notificationService;
        this.secureTokenService = secureTokenService;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountProfileResponse getCurrentUserProfile() {
        return accountMapper.toAccountProfile(loadAuthenticatedUser());
    }

    @Override
    @Transactional
    public AccountProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = loadAuthenticatedUser();
        user.setName(request.name());
        User saved = userRepository.save(user);

        log.info("Profile updated: userId={}", saved.getId());
        return accountMapper.toAccountProfile(saved);
    }

    /**
     * {@code AuthenticationManager} is deliberately not used here, unlike
     * login — this request is already authenticated (it reached this method
     * through the JWT filter chain), so there's nothing left to
     * authenticate. Re-running the full {@code AuthenticationManager} path
     * would also needlessly re-trigger login-specific side effects (failed-
     * attempt tracking, lockout checks) that don't belong to an
     * already-authenticated request. A direct
     * {@code passwordEncoder.matches(raw, hash)} check is the correct tool
     * for "does this value match what's already stored," the same
     * comparison {@code DaoAuthenticationProvider} performs internally
     * during login.
     */
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = loadAuthenticatedUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation do not match.");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        // A password change is itself a signal the account may have been
        // compromised (07-Security-Architecture.md §3.6) — tokenVersion
        // invalidates every outstanding access token on its next
        // verification, and revoking every refresh token below closes the
        // other half: no already-issued session, of any kind, survives this
        // call. Both happen in this same @Transactional method, so a
        // failure partway through leaves neither half applied.
        user.incrementTokenVersion();
        authenticationService.revokeAllRefreshTokensForUser(user);
        User saved = userRepository.save(user);

        log.info("Password changed: userId={}", saved.getId());
    }

    /**
     * Persists nothing and takes no branch observable from the outside when
     * {@code request.email()} doesn't resolve to a user — {@link
     * UserRepository#findByEmailIgnoreCase} returning empty short-circuits
     * the whole body via {@code ifPresent}, so the method returns the exact
     * same way (successfully, having done nothing visible) whether the
     * email exists or not. Revealing that difference is exactly what
     * Milestone 4's design forbids (account enumeration via this endpoint).
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            String rawToken = secureTokenService.generateToken();
            String tokenHash = secureTokenService.hashToken(rawToken);
            Instant expiresAt = Instant.now().plus(PASSWORD_RESET_TOKEN_TTL);

            passwordResetTokenRepository.save(new PasswordResetToken(user, tokenHash, expiresAt));
            notificationService.sendPasswordResetEmail(user.getEmail(), rawToken);

            log.info("Password reset requested: userId={}", user.getId());
        });
    }

    /**
     * "Missing," "expired," and "already used" are collapsed into the same
     * {@link InvalidTokenException} with the same message — reported
     * separately, a caller could use the difference to probe whether a
     * specific token value was ever issued at all (Milestone 4 design,
     * Change 2), the same reasoning {@code forgotPassword} applies to email
     * existence.
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHash(secureTokenService.hashToken(request.token()))
                .orElseThrow(() -> new InvalidTokenException(INVALID_TOKEN_MESSAGE));

        Instant now = Instant.now();
        if (resetToken.isExpired(now) || resetToken.isUsed()) {
            throw new InvalidTokenException(INVALID_TOKEN_MESSAGE);
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("New password and confirmation do not match.");
        }

        User user = resetToken.getUser();
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementTokenVersion();
        authenticationService.revokeAllRefreshTokensForUser(user);
        userRepository.save(user);

        resetToken.markUsed(now);
        passwordResetTokenRepository.save(resetToken);

        log.info("Password reset completed: userId={}", user.getId());
    }

    /**
     * Idempotent by delegation: {@link User#markEmailVerified} already
     * guards against re-verification (won't overwrite {@code
     * emailVerifiedAt}, won't throw), so this method never needs to branch
     * on "is the user already verified" itself — it always runs the same
     * steps, and redeeming an already-redeemed-for-verification account is
     * simply a no-op on the {@code User} half while the token is still
     * marked used, exactly as if this were the first time.
     */
    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken token = emailVerificationTokenRepository
                .findByTokenHash(secureTokenService.hashToken(request.token()))
                .orElseThrow(() -> new InvalidTokenException(INVALID_TOKEN_MESSAGE));

        Instant now = Instant.now();
        if (token.isExpired(now) || token.isUsed()) {
            throw new InvalidTokenException(INVALID_TOKEN_MESSAGE);
        }

        User user = token.getUser();
        user.markEmailVerified(now);
        userRepository.save(user);

        token.markUsed(now);
        emailVerificationTokenRepository.save(token);

        log.info("Email verified: userId={}", user.getId());
    }

    @Override
    @Transactional
    public void resendVerification() {
        User user = loadAuthenticatedUser();
        if (user.isEmailVerified()) {
            return;
        }

        String rawToken = secureTokenService.generateToken();
        String tokenHash = secureTokenService.hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(EMAIL_VERIFICATION_TOKEN_TTL);

        emailVerificationTokenRepository.save(new EmailVerificationToken(user, tokenHash, expiresAt));
        notificationService.sendEmailVerificationEmail(user.getEmail(), rawToken);

        log.info("Verification email resent: userId={}", user.getId());
    }

    /**
     * Restores account access only — deliberately touches nothing else.
     * Activation and email verification are separate concerns (Milestone 4
     * design, Change 3): an admin re-enabling a deactivated account is not a
     * statement about whether that account's email was ever confirmed, so
     * {@code emailVerified} is left exactly as it was. Also does not revoke
     * sessions or touch {@code tokenVersion} — unlike deactivation,
     * activation only ever expands what an account can do, so there is no
     * already-issued token whose continued validity would be unsafe.
     */
    @Override
    @Transactional
    public void activateUser(Long userId) {
        User user = findUserOrThrow(userId);
        if (user.getStatus() == UserStatus.ACTIVE) {
            return;
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("User activated: userId={}", userId);
    }

    /**
     * Deactivation revokes active sessions because it's the opposite case
     * from activation: an admin is deliberately cutting off an account's
     * access, and a still-valid access/refresh token from before that
     * decision would let the account keep working until natural token
     * expiry — the same "no already-issued session survives this call"
     * requirement {@link #changePassword} and {@link #resetPassword} apply
     * to their own security-invalidating events.
     */
    @Override
    @Transactional
    public void deactivateUser(Long userId) {
        User user = findUserOrThrow(userId);
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            return;
        }
        user.setStatus(UserStatus.DEACTIVATED);
        user.incrementTokenVersion();
        authenticationService.revokeAllRefreshTokensForUser(user);
        userRepository.save(user);

        log.info("User deactivated: userId={}", userId);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /**
     * Resolves "the caller" to their current, persisted {@link User} row —
     * never the {@link UserPrincipal} sitting in the {@code SecurityContext}
     * directly. The principal is a snapshot taken when the access token was
     * issued/last re-verified (see {@code JwtAuthenticationFilter}); a
     * profile response must reflect whatever is true in the database right
     * now, including edits made in this same request's own transaction, so
     * every self-service method re-reads the row instead of trusting that
     * snapshot.
     * <p>
     * A missing row for an authenticated principal means the user was
     * deleted out from under a still-valid token — an invariant violation
     * rather than a normal "not found," but reported with the same
     * {@link ResourceNotFoundException} the rest of the codebase already
     * uses for "id doesn't resolve to a row," so the Global Exception
     * Handler needs no new case.
     */
    private User loadAuthenticatedUser() {
        UserPrincipal principal =
                (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.userId()));
    }
}
