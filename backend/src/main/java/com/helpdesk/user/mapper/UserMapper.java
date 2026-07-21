package com.helpdesk.user.mapper;

import com.helpdesk.user.dto.request.CreateUserRequest;
import com.helpdesk.user.dto.request.UpdateUserRequest;
import com.helpdesk.user.dto.response.UserResponse;
import com.helpdesk.user.entity.Role;
import com.helpdesk.user.entity.User;

/**
 * Structural DTO ⇄ Entity conversion only (11-Development-Rules.md §5.5) —
 * no business rule, no repository access, no password encoding. The Service
 * resolves the {@link Role} (via {@code RoleRepository}) and hashes the
 * password (via the injected {@code PasswordEncoder}) before calling into
 * this mapper; this interface only ever receives already-resolved values.
 * <p>
 * Declared as an interface with a hand-written {@code UserMapperImpl}
 * (current pre-MapStruct state, §5.5/§20) so that adopting MapStruct later
 * is a drop-in replacement of the implementation — every call site
 * (`userMapper.toResponse(user)`, etc.) stays unchanged.
 */
public interface UserMapper {

    /**
     * Builds a new, transient {@link User} from a create request. Never
     * called with a raw plaintext password — {@code passwordHash} is the
     * Service's already-encoded value.
     */
    User toEntity(CreateUserRequest request, String passwordHash, Role role);

    /**
     * Applies an update request onto an already-managed {@link User} in
     * place. Only {@code name}/{@code email}/{@code role} are touched —
     * {@code id}, {@code passwordHash}, {@code status}, and the audit
     * timestamps are left exactly as they were.
     */
    void updateEntity(User user, UpdateUserRequest request, Role role);

    /**
     * Projects a {@link User} to its API-safe response shape. Deliberately
     * has no line mapping {@code passwordHash} — omission here is what
     * keeps it structurally impossible for that field to reach a client.
     */
    UserResponse toResponse(User user);
}
