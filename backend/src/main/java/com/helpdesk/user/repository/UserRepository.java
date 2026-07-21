package com.helpdesk.user.repository;

import com.helpdesk.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code findById}/{@code findAll(Pageable)} are used exactly as
 * {@link JpaRepository} already provides them — deliberately no
 * "exclude {@code DEACTIVATED}" default filter, unlike Ticket's soft-delete
 * convention (05-Database.md §7.4): a deactivated user is a filterable
 * status (04-API-Design.md's {@code GET /admin/users?status=}), not a
 * hidden-by-default row, since historical references (e.g. a ticket's
 * creator) must still resolve a deactivated account's name.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Case-insensitive uniqueness check before creating a user (07-Security-Architecture.md §3.1). */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Same uniqueness check for the update flow, excluding the user's own
     * id — without this, updating a user without changing their email would
     * false-positive a conflict against themselves.
     */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
