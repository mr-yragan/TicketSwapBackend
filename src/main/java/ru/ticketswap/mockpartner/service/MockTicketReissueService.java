package ru.ticketswap.mockpartner.service;

import org.springframework.stereotype.Service;
import ru.ticketswap.mockpartner.dto.MockTicketReissueRequest;
import ru.ticketswap.mockpartner.dto.MockTicketReissueResponse;
import ru.ticketswap.mockpartner.exception.MockPartnerBadRequestException;

@Service
public class MockTicketReissueService {

    private static final String REISSUE_FAILED_REASON = "Mock reissue failed";

    public MockTicketReissueResponse reissue(String organizerCode, MockTicketReissueRequest request) {
        if (request == null) {
            throw new MockPartnerBadRequestException("Request body must not be empty");
        }

        String originalTicketUid = request.originalTicketUid();
        String buyerEmail = request.buyerEmail();
        validateOriginalTicketUid(originalTicketUid);
        validateBuyerEmail(buyerEmail);

        if (originalTicketUid.toUpperCase().contains("FAIL")) {
            return MockTicketReissueResponse.failed(originalTicketUid, organizerCode, REISSUE_FAILED_REASON);
        }

        String newTicketUid = "REISSUED-" + organizerCode + "-" + originalTicketUid;
        return MockTicketReissueResponse.success(originalTicketUid, organizerCode, newTicketUid);
    }

    private void validateOriginalTicketUid(String originalTicketUid) {
        if (originalTicketUid == null || originalTicketUid.isBlank()) {
            throw new MockPartnerBadRequestException("originalTicketUid must not be blank");
        }
    }

    private void validateBuyerEmail(String buyerEmail) {
        if (buyerEmail == null || buyerEmail.isBlank()) {
            throw new MockPartnerBadRequestException("buyerEmail must not be blank");
        }
    }
}
