package ru.ticketswap.mockpartner.service;

import org.springframework.stereotype.Service;
import ru.ticketswap.event.Event;
import ru.ticketswap.mockpartner.data.MockPartnerDataProvider;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyRequest;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyResponse;
import ru.ticketswap.mockpartner.exception.MockPartnerBadRequestException;

@Service
public class MockTicketVerificationService {

    private static final int VALIDATION_BUCKETS = 100;
    private static final int VALID_THRESHOLD = 85;
    private static final String INVALID_REASON = "Mock-проверка не пройдена";

    private final MockPartnerDataProvider mockPartnerDataProvider;

    public MockTicketVerificationService(MockPartnerDataProvider mockPartnerDataProvider) {
        this.mockPartnerDataProvider = mockPartnerDataProvider;
    }

    public MockTicketVerifyResponse verify(String organizerCode, MockTicketVerifyRequest request) {
        if (request == null) {
            throw new MockPartnerBadRequestException("Тело запроса не должно быть пустым");
        }

        String ticketUid = request.ticketUid();
        validateTicketUid(ticketUid);
        String eventId = normalizeEventId(request.eventId());
        validateEventBelongsToOrganizer(organizerCode, eventId);

        String normalizedTicketUid = ticketUid.trim().toUpperCase();
        if (normalizedTicketUid.contains("INVALID")) {
            return MockTicketVerifyResponse.invalid(ticketUid, organizerCode, eventId, INVALID_REASON);
        }
        if (normalizedTicketUid.contains("VALID") || normalizedTicketUid.contains("OK")) {
            return MockTicketVerifyResponse.valid(ticketUid, organizerCode, eventId);
        }

        int hash = ticketUid.hashCode();
        int bucket = Math.floorMod(hash, VALIDATION_BUCKETS);
        boolean valid = bucket < VALID_THRESHOLD;

        return valid
                ? MockTicketVerifyResponse.valid(ticketUid, organizerCode, eventId)
                : MockTicketVerifyResponse.invalid(ticketUid, organizerCode, eventId, INVALID_REASON);
    }

    private void validateTicketUid(String ticketUid) {
        if (ticketUid == null || ticketUid.isBlank()) {
            throw new MockPartnerBadRequestException("UID билета не должен быть пустым");
        }
    }

    private String normalizeEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        return eventId.trim();
    }

    private void validateEventBelongsToOrganizer(String organizerCode, String eventId) {
        if (eventId == null) {
            return;
        }
        boolean exists = mockPartnerDataProvider.getEventsByOrganizerCode(organizerCode).stream()
                .map(Event::getEventId)
                .anyMatch(existingEventId -> existingEventId != null && existingEventId.equalsIgnoreCase(eventId));
        if (!exists) {
            throw new MockPartnerBadRequestException("Мероприятие не найдено у этого организатора");
        }
    }
}
