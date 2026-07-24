package com.helpdesk.account.controller;

import com.helpdesk.account.dto.request.ChangePasswordRequest;
import com.helpdesk.account.dto.request.ForgotPasswordRequest;
import com.helpdesk.account.dto.request.ResetPasswordRequest;
import com.helpdesk.account.dto.request.UpdateProfileRequest;
import com.helpdesk.account.dto.request.VerifyEmailRequest;
import com.helpdesk.account.dto.response.AccountProfileResponse;
import com.helpdesk.account.service.AccountService;
import com.helpdesk.common.ApiResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.constant.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account endpoints (Milestone 4) — every method here only binds, validates
 * via {@code @Valid}, delegates to {@link AccountService}, and shapes the
 * response. No business rule is decided in this class: whose profile it is,
 * which fields are editable, how a password is verified/changed, what makes
 * a reset/verification token redeemable — all of that already lives in
 * {@code AccountServiceImpl}, the same "Controller only binds and shapes"
 * convention {@code RoleController}/{@code UserController} already follow.
 * Validation stays entirely declarative ({@code @Valid} plus the DTOs' own
 * Bean Validation annotations) rather than re-checked here, so there is
 * exactly one place a request shape can be judged invalid.
 * <p>
 * Mixed authentication requirement, by necessity rather than convenience:
 * {@code /me}, {@code /profile}, {@code /password}, and
 * {@code /resend-verification} require an existing session, enforced via
 * {@code @PreAuthorize("isAuthenticated()")} (Phase 2, Milestone 5) — the
 * last of these authenticated (not public-by-email) specifically so it can
 * only ever resend to the caller's own address, never anyone else's.
 * {@code /forgot-password}, {@code /reset-password}, and
 * {@code /verify-email} run before a session exists (the caller has no
 * session yet, or is presenting a possessed token instead of one) and are
 * listed as public paths in {@code SecurityConfig} — no
 * {@code @PreAuthorize} on those three. {@code activate}/{@code deactivate}
 * are admin-only, enforced via {@code @PreAuthorize("hasRole('ADMIN')")} on
 * each method — this class still performs no authorization decision
 * itself, the same "Controller never decides, only enforces-by-declaration"
 * split every other endpoint here follows; {@code activateUser}/
 * {@code deactivateUser} don't even receive a principal, only the target
 * {@code userId} from the path, since who's allowed to call them is Spring
 * Security's job, not this class's. {@code forgotPassword} in particular
 * performs no branching in this class — it calls
 * {@code accountService.forgotPassword(request)} and returns the same
 * fixed response unconditionally, because whether the email exists is a
 * fact the Service layer already refuses to expose; a controller-level
 * branch on the outcome would reintroduce the exact leak the Service was
 * written to avoid, even if the branch only ever executed identical-looking
 * code on both paths.
 * <p>
 * Deliberately carries no {@code @SecurityRequirement} annotation on any
 * endpoint, authenticated or not. The only scheme {@code OpenApiConfig}
 * currently defines ({@code bearerAuth}) documents a classic
 * {@code Authorization: Bearer} header, but this application actually
 * authenticates via an HttpOnly access-token cookie
 * ({@code JwtAuthenticationFilter}) — applying that scheme would make the
 * generated Swagger doc actively wrong, not just incomplete. No controller
 * in this codebase uses {@code @SecurityRequirement} yet for the same
 * reason.
 * <p>
 * TODO(Milestone 6 — Security Hardening): define a cookie-based OpenAPI
 * security scheme that accurately models this application's actual
 * authentication mechanism, then apply {@code @SecurityRequirement} to
 * {@code /me}, {@code /profile}, and {@code /password} (never to the three
 * public endpoints below).
 */
@Tag(name = "Account")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Get the current user's profile")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Authenticated user no longer exists")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AccountProfileResponse>> getCurrentUserProfile() {
        return ResponseEntity.ok(ApiResponse.success(accountService.getCurrentUserProfile()));
    }

    @Operation(summary = "Update the current user's profile",
            description = "Editable fields only (currently: name). Email, role, status, and password are not modifiable here.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Authenticated user no longer exists")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<AccountProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        AccountProfileResponse updated = accountService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Change the current user's password",
            description = "Verifies the current password, then replaces it. No new session is issued - "
                    + "the account is logged out of every device and must authenticate again.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Validation failed, current password is incorrect, or the new password matches the current one")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Authenticated user no longer exists")
    @PreAuthorize("isAuthenticated()")
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.UPDATED));
    }

    @Operation(summary = "Request a password reset",
            description = "Always returns the same response, whether or not the email belongs to an account - "
                    + "that fact is never revealed. If it does, a single-use, time-limited reset link is sent.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request accepted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountService.forgotPassword(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("If an account with that email exists, a password reset link has been sent."));
    }

    @Operation(summary = "Redeem a password reset token",
            description = "No new session is issued - the account is logged out of every device and must authenticate again.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Validation failed, or the new password matches the current one")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "The token is missing, expired, or already used")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.UPDATED));
    }

    @Operation(summary = "Redeem an email verification token",
            description = "Idempotent: redeeming a token for an already-verified account is not an error.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
            description = "The token is missing, expired, or already used")
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountService.verifyEmail(request);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.SUCCESS));
    }

    @Operation(summary = "Resend the email verification link",
            description = "Sends a fresh verification token to the caller's own email. A no-op if already verified.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request processed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Authenticated user no longer exists")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification() {
        accountService.resendVerification();
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.SUCCESS));
    }

    @Operation(summary = "Activate a user account (admin)",
            description = "Restores account access by setting status to ACTIVE. A no-op if already ACTIVE.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User activated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(
            @Parameter(description = "User identifier", example = "1") @PathVariable Long userId) {
        accountService.activateUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.UPDATED));
    }

    @Operation(summary = "Deactivate a user account (admin)",
            description = "Sets status to DEACTIVATED and revokes every active session. A no-op if already DEACTIVATED.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deactivated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @Parameter(description = "User identifier", example = "1") @PathVariable Long userId) {
        accountService.deactivateUser(userId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.DELETED));
    }
}
