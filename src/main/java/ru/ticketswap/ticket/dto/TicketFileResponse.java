package ru.ticketswap.ticket.dto;

import ru.ticketswap.ticket.TicketFile;

import java.time.Instant;

public record TicketFileResponse(
        Long id,
        String originalName,
        String contentType,
        Long sizeBytes,
        Instant uploadedAt
) {

    public static TicketFileResponse fromEntity(TicketFile ticketFile) {
        return new TicketFileResponse(
                ticketFile.getId(),
                ticketFile.getOriginalName(),
                ticketFile.getContentType(),
                ticketFile.getSizeBytes(),
                ticketFile.getCreatedAt()
        );
    }
}
