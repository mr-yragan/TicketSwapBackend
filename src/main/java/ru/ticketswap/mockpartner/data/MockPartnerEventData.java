package ru.ticketswap.mockpartner.data;

import java.time.Instant;


public record MockPartnerEventData(
        long externalEventId,
        String name,
        Instant startsAt,
        String organizerCode,
        String venueName,
        String venueAddress,
        String venueTimezone
) {
}
