package ru.ticketswap.ticket.dto;

import ru.ticketswap.ticket.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record TicketLotResponse(
        Long id,
        String uid,
        String eventName,
        LocalDateTime eventDate,
        BigDecimal originalPrice,
        BigDecimal resalePrice,
        TicketStatus status,
        String sellerEmail,
        Instant createdAt
) {
}
