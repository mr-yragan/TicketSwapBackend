package ru.ticketswap.mockpartner;

import org.junit.jupiter.api.Test;
import ru.ticketswap.mockpartner.data.MockPartnerDataProvider;
import ru.ticketswap.mockpartner.exception.MockPartnerOrganizerNotFoundException;
import ru.ticketswap.mockpartner.service.MockPartnerOrganizerResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockPartnerOrganizerResolverTest {

    private final MockPartnerOrganizerResolver resolver =
            new MockPartnerOrganizerResolver(new MockPartnerDataProvider());

    @Test
    void requireSupportedOrganizerAcceptsOrg1AndOrg2() {
        assertEquals("org1", resolver.requireSupportedOrganizer("org1"));
        assertEquals("org2", resolver.requireSupportedOrganizer("org2"));
    }

    @Test
    void requireSupportedOrganizerRejectsUnknownOrganizer() {
        MockPartnerOrganizerNotFoundException ex = assertThrows(
                MockPartnerOrganizerNotFoundException.class,
                () -> resolver.requireSupportedOrganizer("org3")
        );

        assertEquals("Organizer not found: org3", ex.getMessage());
    }
}
