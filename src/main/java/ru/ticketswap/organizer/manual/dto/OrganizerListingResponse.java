package ru.ticketswap.organizer.manual.dto;

import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrganizerListingResponse(
        Long id,
        String uid,
        TicketStatus status,
        String eventName,
        LocalDateTime eventDate,
        String venue,
        BigDecimal price,
        UserBrief seller,
        UserBrief buyer,
        boolean hasTicketFile,
        int ticketFilesCount,
        String reissuedTicketUid,
        boolean hasReissuedTicketFile
) {

    public static OrganizerListingResponse fromEntity(TicketLot listing) {
        return new OrganizerListingResponse(
                listing.getId(),
                listing.getUid(),
                listing.getStatus(),
                listing.getEventName(),
                listing.getEventDate(),
                formatVenue(listing.getVenueName(), listing.getVenueCity()),
                listing.getResalePrice(),
                listing.getSeller() == null ? null : new UserBrief(
                        listing.getSeller().getId(),
                        listing.getSeller().getEmail(),
                        listing.getSeller().getLogin()
                ),
                listing.getBuyer() == null ? null : new UserBrief(
                        listing.getBuyer().getId(),
                        listing.getBuyer().getEmail(),
                        listing.getBuyer().getLogin()
                ),
                listing.hasTicketFile(),
                listing.getTicketFilesCount(),
                listing.getReissuedTicketUid(),
                listing.hasReissuedTicketFile()
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

    public record UserBrief(
            Long id,
            String email,
            String login
    ) {
    }
}
