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
            assertEquals("Mock-проверка не пройдена", response.reason());
        }
    }

    @Test
    void verifyRejectsNullEmptyAndBlankTicketUid() {
        assertEquals(
                "UID билета не должен быть пустым",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.verify("org1", new MockTicketVerifyRequest(null))).getMessage()
        );
        assertEquals(
                "UID билета не должен быть пустым",
                assertThrows(MockPartnerBadRequestException.class,
                        () -> service.verify("org1", new MockTicketVerifyRequest(""))).getMessage()
        );
        assertEquals(
                "UID билета не должен быть пустым",
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

        assertEquals("Тело запроса не должно быть пустым", ex.getMessage());
        assertNotNull(ex);
    }
}
