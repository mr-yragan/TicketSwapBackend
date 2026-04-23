package ru.ticketswap.mockpartner;

import org.junit.jupiter.api.Test;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyRequest;
import ru.ticketswap.mockpartner.dto.MockTicketVerifyResponse;
import ru.ticketswap.mockpartner.exception.MockPartnerBadRequestException;
import ru.ticketswap.mockpartner.service.MockTicketVerificationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockTicketVerificationServiceTest {

    private final MockTicketVerificationService service = new MockTicketVerificationService();

    @Test
    void verifyReturnsSameResultForSameTicketUid() {
        MockTicketVerifyRequest request = new MockTicketVerifyRequest("TICKET-12345");

        MockTicketVerifyResponse first = service.verify("org1", request);
        MockTicketVerifyResponse second = service.verify("org1", request);

        assertEquals(first, second);
    }

    @Test
    void verifyDependsOnlyOnTicketUidAndNotOnOrganizer() {
        MockTicketVerifyRequest request = new MockTicketVerifyRequest("TICKET-12345");

        MockTicketVerifyResponse org1Response = service.verify("org1", request);
        MockTicketVerifyResponse org2Response = service.verify("org2", request);

        assertEquals(org1Response.valid(), org2Response.valid());
        assertEquals(org1Response.reason(), org2Response.reason());
        assertEquals("org1", org1Response.organizerCode());
        assertEquals("org2", org2Response.organizerCode());
    }

    @Test
    void verifyProducesStableReasonBasedOnValidity() {
        MockTicketVerifyResponse response = service.verify("org1", new MockTicketVerifyRequest("TICKET-12345"));

        if (response.valid()) {
            assertNull(response.reason());
        } else {
            assertEquals("Mock validation failed", response.reason());
        }
    }

    @Test
    void verifyRejectsNullEmptyAndBlankTicketUid() {
        assertEquals(
                "ticketUid must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.verify("org1", new MockTicketVerifyRequest(null))).getMessage()
        );
        assertEquals(
                "ticketUid must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.verify("org1", new MockTicketVerifyRequest(""))).getMessage()
        );
        assertEquals(
                "ticketUid must not be blank",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.verify("org1", new MockTicketVerifyRequest("   "))).getMessage()
        );
    }

    @Test
    void verifyRejectsEmptyBody() {
        MockPartnerBadRequestException ex = assertThrows(
                MockPartnerBadRequestException.class,
                () -> service.verify("org1", null)
        );

        assertEquals("Request body must not be empty", ex.getMessage());
        assertNotNull(ex);
    }
}
