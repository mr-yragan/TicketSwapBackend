package ru.ticketswap.mockpartner.service;

import org.springframework.stereotype.Component;
import ru.ticketswap.mockpartner.data.MockPartnerEventData;
import ru.ticketswap.mockpartner.dto.MockPartnerEventResponse;

import java.time.ZoneId;

@Component
public class MockPartnerEventMapper {

    public MockPartnerEventResponse toResponse(MockPartnerEventData event) {
        ZoneId venueZone = ZoneId.of(event.venueTimezone());

        return new MockPartnerEventResponse(
                event.externalEventId(),
                event.name(),
                event.startsAt(),
                event.startsAt().atZone(venueZone).toLocalDate(),
                event.organizerCode(),
                event.venueName(),
                event.venueAddress(),
                event.venueTimezone()
        );
    }
}
