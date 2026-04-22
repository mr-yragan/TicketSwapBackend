package ru.ticketswap.ticket.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

public record ListingDetailsResponse(
        Long id,
        String eventName,
        LocalDateTime eventDate,
        String venue,
        BigDecimal price,
        boolean verified,
        String additionalInfo,
        String organizerName,
        String sellerComment,
        SellerInfo seller,
        boolean hasTicketFile,
        int ticketFilesCount
) {

    public record SellerInfo(
            String displayName,
            Instant memberSince
    ) {
    }
}
