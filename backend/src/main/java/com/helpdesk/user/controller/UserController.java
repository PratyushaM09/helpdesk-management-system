package com.helpdesk.user.controller;

import com.helpdesk.account.service.AccountService;
import com.helpdesk.common.ApiResponse;
import com.helpdesk.common.PageResponse;
import com.helpdesk.constant.ApiConstants;
import com.helpdesk.constant.ResponseMessages;
import com.helpdesk.user.dto.request.CreateUserRequest;
import com.helpdesk.user.dto.request.UpdateUserRequest;
import com.helpdesk.user.dto.response.UserResponse;
import com.helpdesk.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * User Domain CRUD (Phase 2, Milestone 1); every method only binds,
 * delegates, and shapes the response. {@code deactivateUser} is the one
 * exception to "delegates to {@link UserService}" — it delegates to
 * {@link AccountService} instead (Milestone 4 architectural decision): a
 * deactivation must bump {@code tokenVersion} and revoke every refresh
 * token, and {@code AccountService} is the single implementation of that
 * business rule, also used by the admin {@code deactivateUser}/{@code
 * activateUser} operations. Keeping a second, weaker deactivation path on
 * {@code UserService} would let the same real-world action ("deactivate
 * this user") mean two different things depending which endpoint was
 * called.
 */
@Tag(name = "User")
@RestController
@RequestMapping(ApiConstants.API_BASE_PATH + "/users")
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    public UserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    @Operation(summary = "Create a user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        URI location = URI.create(ApiConstants.API_BASE_PATH + "/users/" + created.id());
        return ResponseEntity.created(location).body(ApiResponse.success(ResponseMessages.CREATED, created));
    }

    @Operation(summary = "Get a user by id")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @Parameter(description = "User identifier", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @Operation(summary = "List users, paginated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paged user list")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getUsers(
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<UserResponse> users = userService.getUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(users)));
    }

    @Operation(summary = "Update a user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already registered")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "User identifier", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse updated = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(ResponseMessages.UPDATED, updated));
    }

    @Operation(summary = "Deactivate a user",
            description = "Soft delete: sets status to DEACTIVATED, revokes every session. The row is never removed.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User deactivated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @Parameter(description = "User identifier", example = "1") @PathVariable Long id) {
        accountService.deactivateUser(id);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(ResponseMessages.DELETED));
    }
}
