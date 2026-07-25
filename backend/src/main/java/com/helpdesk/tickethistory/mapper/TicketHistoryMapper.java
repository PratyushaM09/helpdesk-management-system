package com.helpdesk.tickethistory.mapper;

import com.helpdesk.tickethistory.dto.response.TicketHistoryResponse;
import com.helpdesk.tickethistory.entity.TicketHistory;

/**
 * Converts {@link TicketHistory} entities into API response DTOs. No request
 * mapping - history rows are only ever written by the Service layer as a
 * side effect of some other action, never created directly from a client
 * request. Performs no filtering - deciding which rows a caller may see
 * (e.g. excluding {@code internal} entries from a USER's view) is a
 * Service-layer concern, applied before this mapper ever sees the list.
 */
public interface TicketHistoryMapper {

    /** Projects a {@link TicketHistory} row to its API-safe response shape. */
    TicketHistoryResponse toResponse(TicketHistory ticketHistory);
}
