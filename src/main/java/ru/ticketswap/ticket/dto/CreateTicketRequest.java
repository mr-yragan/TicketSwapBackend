package ru.ticketswap.ticket.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTicketRequest(
        String uid,
        String eventName,
        LocalDateTime eventDate,
        BigDecimal originalPrice,
        BigDecimal resalePrice
) {}
