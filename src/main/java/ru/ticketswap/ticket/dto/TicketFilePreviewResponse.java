package ru.ticketswap.ticket.dto;

import java.time.Instant;

public record TicketFilePreviewResponse(
        Long fileId,
        String url,
        Instant expiresAt,
        String contentType,
        Long sizeBytes,
        Instant createdAt
) {
}
