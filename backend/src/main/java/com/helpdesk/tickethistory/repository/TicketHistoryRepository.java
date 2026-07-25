package com.helpdesk.tickethistory.repository;

import com.helpdesk.tickethistory.entity.TicketHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Extends the bare Spring Data {@link Repository} marker - not
 * {@code JpaRepository} - so no {@code delete}/{@code deleteById} is ever
 * inherited. Only read/write methods are declared, all still backed by
 * Spring Data's standard repository proxy; this is the enforcement
 * mechanism for ADR-0006's append-only contract, not just a documented
 * convention - adding {@link #findByTicketIdAndInternalFalse} (Service
 * Milestone 3) doesn't weaken that, since it's still read-only.
 */
public interface TicketHistoryRepository extends Repository<TicketHistory, Long> {

    TicketHistory save(TicketHistory ticketHistory);

    Page<TicketHistory> findByTicketId(Long ticketId, Pageable pageable);

    /** Backs {@code TicketHistoryServiceImpl.getHistory}'s USER-facing filter - excluding internal rows at the query level keeps pagination totals correct. */
    Page<TicketHistory> findByTicketIdAndInternalFalse(Long ticketId, Pageable pageable);
}
