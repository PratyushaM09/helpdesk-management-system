package com.helpdesk.role.repository;

import com.helpdesk.role.entity.Role;
import com.helpdesk.role.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@code findByName} resolves a requested {@link RoleName} to its persisted
 * {@link Role} row — used both by the User service (attaching a role at
 * creation/update) and by {@code RoleSeeder} (idempotent seed check).
 * {@code findById}/{@code findAll(Pageable)} are used exactly as
 * {@link JpaRepository} already provides them for Role administration
 * (Phase 2, Milestone 2).
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
