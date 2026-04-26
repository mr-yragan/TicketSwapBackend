package ru.ticketswap.mockpartner.service;

import org.springframework.stereotype.Component;
import ru.ticketswap.event.Event;
import ru.ticketswap.mockpartner.dto.MockPartnerEventResponse;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.venue.Venue;

@Component
public class MockPartnerEventMapper {

    public MockPartnerEventResponse toResponse(Event event) {
        Organizer organizer = event.getOrganizer();
        Venue venue = event.getVenue();

        return new MockPartnerEventResponse(
                event.getEventId(),
                event.getName(),
                event.getStartsAt(),
                event.getDate(),
                organizer.getApiKey(),
                venue.getName(),
                venue.getAddress(),
                venue.getTimezone()
        );
    }
}
