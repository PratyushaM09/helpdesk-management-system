package com.helpdesk.tickethistory.service.impl;

import com.helpdesk.role.entity.RoleName;
import com.helpdesk.security.UserPrincipal;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.tickethistory.dto.response.TicketHistoryResponse;
import com.helpdesk.tickethistory.entity.TicketHistory;
import com.helpdesk.tickethistory.mapper.TicketHistoryMapper;
import com.helpdesk.tickethistory.repository.TicketHistoryRepository;
import com.helpdesk.tickethistory.service.TicketHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketHistoryServiceImpl implements TicketHistoryService {

    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketService ticketService;
    private final TicketHistoryMapper ticketHistoryMapper;

    public TicketHistoryServiceImpl(TicketHistoryRepository ticketHistoryRepository, TicketService ticketService,
                                     TicketHistoryMapper ticketHistoryMapper) {
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.ticketService = ticketService;
        this.ticketHistoryMapper = ticketHistoryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketHistoryResponse> getHistory(Long ticketId, Pageable pageable) {
        ticketService.validateTicketAccess(ticketId);

        UserPrincipal principal = currentPrincipal();
        Page<TicketHistory> history = principal.role() == RoleName.USER
                ? ticketHistoryRepository.findByTicketIdAndInternalFalse(ticketId, pageable)
                : ticketHistoryRepository.findByTicketId(ticketId, pageable);

        return history.map(ticketHistoryMapper::toResponse);
    }

    private UserPrincipal currentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
