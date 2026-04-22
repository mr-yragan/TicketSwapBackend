package ru.ticketswap.ticket.dto;

import java.time.Instant;

public record TicketFileDownloadUrlResponse(
        Long fileId,
        String url,
        Instant expiresAt,
        String originalName,
        String contentType,
        Long sizeBytes
) {
}
