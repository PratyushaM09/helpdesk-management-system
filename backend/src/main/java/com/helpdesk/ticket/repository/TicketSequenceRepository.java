package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.TicketSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * {@code findByYear} acquires a {@code SELECT ... FOR UPDATE} row lock
 * (ADR-0010-style optimistic locking is a poor fit here — a lost-update
 * retry loop on a counter is more complex than briefly serializing
 * ticket-number generation via a pessimistic lock on one narrow row).
 * Held for the rest of the calling transaction, so two concurrent ticket
 * creations in the same year never observe/increment the same
 * {@code nextValue}.
 */
public interface TicketSequenceRepository extends JpaRepository<TicketSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TicketSequence> findByYear(int year);
}
