package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every method here (including inherited {@code findById}/{@code findAll})
 * transparently excludes soft-deleted rows via {@code Ticket}'s
 * {@code @SQLRestriction} (ADR-0005) - no method needs to repeat that filter.
 * {@code findByCreatedById}/{@code findByAssignedToId} back
 * {@code TicketServiceImpl.getTickets}'s role-scoped visibility filtering
 * (USER sees own tickets, SUPPORT_ENGINEER sees assigned tickets) - no
 * further filter/search query methods yet, those arrive with a later
 * milestone's search/filter work.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Page<Ticket> findByCreatedById(Long createdById, Pageable pageable);

    Page<Ticket> findByAssignedToId(Long assignedToId, Pageable pageable);
}
