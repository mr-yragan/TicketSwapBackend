package ru.ticketswap.mockpartner.service;

import org.springframework.stereotype.Service;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyRequest;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyResponse;
import ru.ticketswap.mockpartner.exception.MockPartnerBadRequestException;

@Service
public class MockTicketVerificationService {

    private static final int VALIDATION_BUCKETS = 100;
    private static final int VALID_THRESHOLD = 85;
    private static final String INVALID_REASON = "Mock-проверка не пройдена";

    public MockTicketVerifyResponse verify(String organizerCode, MockTicketVerifyRequest request) {
        if (request == null) {
            throw new MockPartnerBadRequestException("Тело запроса не должно быть пустым");
        }

        String ticketUid = request.ticketUid();
        validateTicketUid(ticketUid);

        int hash = ticketUid.hashCode();
        int bucket = Math.floorMod(hash, VALIDATION_BUCKETS);
        boolean valid = bucket < VALID_THRESHOLD;

        return valid
                ? MockTicketVerifyResponse.valid(ticketUid, organizerCode)
                : MockTicketVerifyResponse.invalid(ticketUid, organizerCode, INVALID_REASON);
    }

    private void validateTicketUid(String ticketUid) {
        if (ticketUid == null) {
            throw new MockPartnerBadRequestException("UID билета не должен быть пустым");
        }
        if (ticketUid.isEmpty()) {
            throw new MockPartnerBadRequestException("UID билета не должен быть пустым");
        }
        if (ticketUid.isBlank()) {
            throw new MockPartnerBadRequestException("UID билета не должен быть пустым");
        }
    }
}
