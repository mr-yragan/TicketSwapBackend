package ru.ticketswap.ticket.dto;

import ru.ticketswap.ticket.TicketStatus;

import java.time.Instant;

public record ListingViewResponse(
        ListingDetailsResponse details,
        TicketStatus status,
        Hold hold
) {

    public record Hold(
            Long id,
            Instant holdUntil
    ) {
    }
}
