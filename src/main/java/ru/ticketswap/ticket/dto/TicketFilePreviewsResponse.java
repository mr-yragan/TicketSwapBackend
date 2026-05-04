package ru.ticketswap.ticket.dto;

import java.util.List;

public record TicketFilePreviewsResponse(
        Long listingId,
        int count,
        List<TicketFilePreviewResponse> previews
) {
}
