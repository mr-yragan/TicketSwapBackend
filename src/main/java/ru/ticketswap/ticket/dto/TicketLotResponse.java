package ru.ticketswap.ticket.dto;

import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketLotResponse(
        Long id,
        String eventName,
        LocalDateTime eventDate,
        String venue,
        BigDecimal price,
        boolean verified
) {

    public static TicketLotResponse fromEntity(TicketLot ticket) {
        String venue = formatVenue(ticket.getVenueName(), ticket.getVenueCity());
        return new TicketLotResponse(
                ticket.getId(),
                ticket.getEventName(),
                ticket.getEventDate(),
                venue,
                ticket.getResalePrice(),
                isVerified(ticket.getStatus())
        );
    }

    private static String formatVenue(String venueName, String venueCity) {
        String name = venueName == null ? "" : venueName.trim();
        String city = venueCity == null ? "" : venueCity.trim();

        if (name.isEmpty()) {
            return city;
        }
        if (city.isEmpty()) {
            return name;
        }
        return name + ", " + city;
    }

    private static boolean isVerified(TicketStatus status) {
        if (status == null) {
            return false;
        }
        return status == TicketStatus.PENDING_RECIPIENT
                || status == TicketStatus.PROCESSING
                || status == TicketStatus.COMPLETED;
    }
}
