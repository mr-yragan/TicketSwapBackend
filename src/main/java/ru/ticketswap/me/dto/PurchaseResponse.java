package ru.ticketswap.me.dto;

import ru.ticketswap.ticket.dto.TicketLotResponse;

import java.time.Instant;

public record PurchaseResponse(
        Long id,
        TicketLotResponse listing,
        Instant holdUntil,
        Instant createdAt
) {
}
