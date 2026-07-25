package com.helpdesk.tickethistory.mapper;

import com.helpdesk.tickethistory.dto.response.TicketHistoryResponse;
import com.helpdesk.tickethistory.entity.TicketHistory;
import org.springframework.stereotype.Component;

@Component
public class TicketHistoryMapperImpl implements TicketHistoryMapper {

    @Override
    public TicketHistoryResponse toResponse(TicketHistory ticketHistory) {
        return new TicketHistoryResponse(
                ticketHistory.getId(),
                ticketHistory.getAction(),
                ticketHistory.getOldValue(),
                ticketHistory.getNewValue(),
                ticketHistory.getActor().getName(),
                ticketHistory.getNote(),
                ticketHistory.getCreatedAt()
        );
    }
}
