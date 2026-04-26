package ru.ticketswap.event.dto;

import ru.ticketswap.event.Event;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.venue.Venue;

import java.time.Instant;
import java.time.LocalDate;

public record OrganizerEventResponse(
        Long id,
        String eventId,
        String name,
        Instant startsAt,
        LocalDate date,
        OrganizerInfo organizer,
        VenueInfo venue
) {

    public static OrganizerEventResponse fromEntity(Event event, boolean includeOrganizer) {
        Organizer organizer = event.getOrganizer();
        Venue venue = event.getVenue();

        return new OrganizerEventResponse(
                event.getId(),
                event.getEventId(),
                event.getName(),
                event.getStartsAt(),
                event.getDate(),
                includeOrganizer
                        ? new OrganizerInfo(organizer.getId(), organizer.getName(), organizer.getApiKey())
                        : null,
                new VenueInfo(venue.getId(), venue.getName(), venue.getAddress(), venue.getTimezone())
        );
    }

    public record OrganizerInfo(
            Long id,
            String name,
            String apiKey
    ) {
    }

    public record VenueInfo(
            Long id,
            String name,
            String address,
            String timezone
    ) {
    }
}
