package ru.ticketswap.ticket.dto;

import java.util.List;

public record TicketFilesResponse(
        Long listingId,
        int count,
        List<TicketFileResponse> files
) {
}
