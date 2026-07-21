package com.helpdesk.user.repository;

import com.helpdesk.user.entity.Role;
import com.helpdesk.user.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Minimal slice for Milestone 1: resolving a requested {@link RoleName} to
 * its persisted {@link Role} row (used both by the User service, to attach a
 * role at creation/update, and by the seed-data initializer, to check
 * whether a role already exists before inserting it). Full Role
 * administration (listing, CRUD) is Milestone 2's scope, not added
 * speculatively here.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
