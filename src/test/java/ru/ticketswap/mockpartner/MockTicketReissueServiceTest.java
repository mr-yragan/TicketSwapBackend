package ru.ticketswap.mockpartner;

import org.junit.jupiter.api.Test;
import ru.ticketswap.mockpartner.dto.MockTicketReissueRequest;
import ru.ticketswap.mockpartner.dto.MockTicketReissueResponse;
import ru.ticketswap.mockpartner.exception.MockPartnerBadRequestException;
import ru.ticketswap.mockpartner.service.MockTicketReissueService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockTicketReissueServiceTest {

    private final MockTicketReissueService service = new MockTicketReissueService();

    @Test
    void reissueReturnsStableNewTicketUid() {
        MockTicketReissueRequest request = new MockTicketReissueRequest("TICKET-12345", "buyer@example.com");

        MockTicketReissueResponse response = service.reissue("org1", request);

        assertTrue(response.success());
        assertEquals("TICKET-12345", response.originalTicketUid());
        assertEquals("REISSUED-org1-TICKET-12345", response.newTicketUid());
        assertEquals("org1", response.organizerCode());
        assertNull(response.reason());
    }

    @Test
    void reissueCanReturnBusinessFailureForFailUid() {
        MockTicketReissueResponse response = service.reissue(
                "org1",
                new MockTicketReissueRequest("TICKET-FAIL-1", "buyer@example.com")
        );

        assertEquals(false, response.success());
        assertNull(response.newTicketUid());
        assertEquals("Mock reissue failed", response.reason());
    }

    @Test
    void reissueRejectsNullEmptyAndBlankOriginalTicketUid() {
        assertEquals(
                "originalTicketUid must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.reissue("org1", new MockTicketReissueRequest(null, "buyer@example.com"))).getMessage()
        );
        assertEquals(
                "originalTicketUid must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.reissue("org1", new MockTicketReissueRequest("", "buyer@example.com"))).getMessage()
        );
        assertEquals(
                "originalTicketUid must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.reissue("org1", new MockTicketReissueRequest("   ", "buyer@example.com"))).getMessage()
        );
    }

    @Test
    void reissueRejectsNullEmptyAndBlankBuyerEmail() {
        assertEquals(
                "buyerEmail must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.reissue("org1", new MockTicketReissueRequest("TICKET-12345", null))).getMessage()
        );
        assertEquals(
                "buyerEmail must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.reissue("org1", new MockTicketReissueRequest("TICKET-12345", ""))).getMessage()
        );
        assertEquals(
                "buyerEmail must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.reissue("org1", new MockTicketReissueRequest("TICKET-12345", "   "))).getMessage()
        );
    }

    @Test
    void reissueRejectsEmptyBody() {
        MockPartnerBadRequestException ex = assertThrows(
                MockPartnerBadRequestException.class,
                () -> service.reissue("org1", null)
        );

        assertEquals("Request body must not be empty", ex.getMessage());
    }
}
