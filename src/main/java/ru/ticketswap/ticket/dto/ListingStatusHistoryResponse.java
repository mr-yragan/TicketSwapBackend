package ru.ticketswap.ticket.dto;

import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.ticket.history.ListingStatusHistory;

import java.time.Instant;

public record ListingStatusHistoryResponse(
        Long id,
        TicketStatus fromStatus,
        TicketStatus toStatus,
        String reason,
        Instant changedAt,
        Actor changedBy
) {

    public static ListingStatusHistoryResponse fromEntity(ListingStatusHistory entry) {
        Actor actor = null;
        if (entry.getChangedByUser() != null) {
            String displayName = entry.getChangedByUser().getLogin();
            if (displayName == null || displayName.isBlank()) {
                displayName = entry.getChangedByUser().getEmail();
            }
            actor = new Actor(
                    entry.getChangedByUser().getId(),
                    displayName,
                    entry.getChangedByUser().getEmail()
            );
        }

        return new ListingStatusHistoryResponse(
                entry.getId(),
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getReason(),
                entry.getChangedAt(),
                actor
        );
    }

    public record Actor(
            Long id,
            String displayName,
            String email
    ) {
    }
}
