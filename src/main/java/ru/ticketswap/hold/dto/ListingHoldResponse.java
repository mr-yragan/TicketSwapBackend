package ru.ticketswap.hold.dto;

import ru.ticketswap.ticket.dto.TicketLotResponse;

import java.time.Instant;

public record ListingHoldResponse(
        Long id,
        TicketLotResponse listing,
        Instant holdUntil,
        Instant createdAt
) {
}
