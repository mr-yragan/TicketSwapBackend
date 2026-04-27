package ru.ticketswap.event.search.dto;

import ru.ticketswap.event.Event;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.venue.Venue;

import java.time.Instant;
import java.time.LocalDate;

public record EventSearchResponse(
        Long id,
        String eventId,
        String name,
        Instant startsAt,
        LocalDate date,
        OrganizerInfo organizer,
        VenueInfo venue,
        ListingDefaults listingDefaults
) {

    public static EventSearchResponse fromEntity(Event event) {
        Organizer organizer = event.getOrganizer();
        Venue venue = event.getVenue();
        return new EventSearchResponse(
                event.getId(),
                event.getEventId(),
                event.getName(),
                event.getStartsAt(),
                event.getDate(),
                new OrganizerInfo(
                        organizer.getId(),
                        organizer.getName(),
                        organizer.getApiKey(),
                        organizer.getVerificationMode().name()
                ),
                new VenueInfo(
                        venue.getId(),
                        venue.getName(),
                        venue.getAddress(),
                        venue.getTimezone()
                ),
                new ListingDefaults(
                        event.getId(),
                        event.getName(),
                        event.getStartsAt(),
                        venue.getName() + ", " + venue.getAddress(),
                        organizer.getId(),
                        organizer.getName(),
                        event.getEventId()
                )
        );
    }

    public record OrganizerInfo(
            Long id,
            String name,
            String apiKey,
            String verificationMode
    ) {
    }

    public record VenueInfo(
            Long id,
            String name,
            String address,
            String timezone
    ) {
    }

    public record ListingDefaults(
            Long selectedEventId,
            String eventName,
            Instant startsAt,
            String venue,
            Long organizerId,
            String organizerName,
            String eventId
    ) {
    }
}
