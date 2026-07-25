package com.helpdesk.ticket.mapper;

import com.helpdesk.ticket.dto.request.CreateTicketRequest;
import com.helpdesk.ticket.dto.request.UpdateTicketRequest;
import com.helpdesk.ticket.dto.response.TicketDetailResponse;
import com.helpdesk.ticket.dto.response.TicketSummaryResponse;
import com.helpdesk.ticket.entity.Category;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class TicketMapperImpl implements TicketMapper {

    @Override
    public Ticket toEntity(CreateTicketRequest request, String ticketNumber, Category category, User createdBy) {
        return new Ticket(
                ticketNumber,
                request.title(),
                request.description(),
                category,
                request.priority(),
                createdBy
        );
    }

    @Override
    public void updateEntity(Ticket ticket, UpdateTicketRequest request) {
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
    }

    @Override
    public TicketSummaryResponse toSummaryResponse(Ticket ticket) {
        User assignedTo = ticket.getAssignedTo();
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory().getName(),
                assignedTo != null ? assignedTo.getName() : null,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }

    @Override
    public TicketDetailResponse toDetailResponse(Ticket ticket) {
        User assignedTo = ticket.getAssignedTo();
        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory().getName(),
                ticket.getCreatedBy().getName(),
                assignedTo != null ? assignedTo.getName() : null,
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
