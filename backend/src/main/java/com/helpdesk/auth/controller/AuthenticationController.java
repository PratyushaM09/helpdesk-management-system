package com.helpdesk.auth.controller;

import com.helpdesk.auth.dto.request.LoginRequest;
import com.helpdesk.auth.dto.response.CsrfTokenResponse;
import com.helpdesk.auth.dto.response.LoginResponse;
import com.helpdesk.auth.service.AuthenticationResult;
import com.helpdesk.auth.service.AuthenticationService;
import com.helpdesk.common.ApiResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.constant.ResponseMessages;
import com.helpdesk.exception.UnauthorizedException;
import com.helpdesk.security.CookieService;
import com.helpdesk.security.SecurityConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authentication endpoints (Phase 2, Milestone 3) — every method here only
 * binds, delegates to exactly one {@link AuthenticationService} call, and
 * attaches the cookies that call already produced. No authentication logic,
 * no JWT handling, no security decisions, and no manual cookie construction
 * live here — see the conversation's login/refresh/logout lifecycle
 * walkthrough for the full reasoning behind each method's shape.
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final CookieService cookieService;

    public AuthenticationController(AuthenticationService authenticationService, CookieService cookieService) {
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
    }

    @Operation(summary = "Log in", description = "Issues access_token, refresh_token, and csrf_token cookies on success.")
    // See HealthController.health()'s comment on why this is
    // @SecurityRequirements, not @Operation(security = {}).
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login succeeded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid email or password")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Account locked")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthenticationResult result = authenticationService.login(request);
        return withCookies(ResponseEntity.ok(), result.cookies())
                .body(ApiResponse.success(result.response()));
    }

    @Operation(summary = "Refresh the access/refresh token pair",
            description = "Rotates the refresh token presented via cookie (SDR-003); reuse of an already-rotated token revokes its entire session family.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tokens refreshed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid, expired, or reused refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = SecurityConstants.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        AuthenticationResult result = authenticationService.refresh(refreshToken);
        return withCookies(ResponseEntity.ok(), result.cookies())
                .body(ApiResponse.success(result.response()));
    }

    @Operation(summary = "Get a CSRF token",
            description = "Issues/rotates the csrf_token cookie and echoes its value in the body — the value must "
                    + "be readable by frontend JS to echo back as X-CSRF-Token, but a frontend on a different "
                    + "subdomain than this API cannot read the cookie directly (document.cookie is scoped to the "
                    + "setting origin). Call on every page load to (re)populate an in-memory copy.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token issued")
    @GetMapping("/csrf-token")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrfToken() {
        ResponseCookie cookie = authenticationService.issueCsrfCookie();
        return withCookies(ResponseEntity.ok(), List.of(cookie))
                .body(ApiResponse.success(new CsrfTokenResponse(cookie.getValue())));
    }

    @Operation(summary = "Log out", description = "Revokes the current refresh token and clears every auth cookie. Idempotent.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = SecurityConstants.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        List<ResponseCookie> cookies = refreshToken != null
                ? authenticationService.logout(refreshToken)
                : cookieService.clearAllCookies();
        return withCookies(ResponseEntity.ok(), cookies)
                .body(ApiResponse.success(ResponseMessages.SUCCESS));
    }

    /** Attaches already-built cookies as Set-Cookie headers — relaying, never constructing. */
    private ResponseEntity.BodyBuilder withCookies(ResponseEntity.BodyBuilder builder, List<ResponseCookie> cookies) {
        cookies.forEach(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie.toString()));
        return builder;
    }
}
