package ru.ticketswap.mockpartner;

import org.junit.jupiter.api.Test;
import ru.ticketswap.mockpartner.data.MockPartnerDataProvider;
import ru.ticketswap.mockpartner.exception.MockPartnerOrganizerNotFoundException;
import ru.ticketswap.mockpartner.service.MockPartnerOrganizerResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockPartnerOrganizerResolverTest {

    private final MockPartnerDataProvider mockPartnerDataProvider = mock(MockPartnerDataProvider.class);
    private final MockPartnerOrganizerResolver resolver = new MockPartnerOrganizerResolver(mockPartnerDataProvider);

    @Test
    void requireSupportedOrganizerAcceptsOrg1AndOrg2() {
        when(mockPartnerDataProvider.isSupportedOrganizer("org1")).thenReturn(true);
        when(mockPartnerDataProvider.isSupportedOrganizer("org2")).thenReturn(true);

        assertEquals("org1", resolver.requireSupportedOrganizer("org1"));
        assertEquals("org2", resolver.requireSupportedOrganizer("org2"));
    }

    @Test
    void requireSupportedOrganizerRejectsUnknownOrganizer() {
        MockPartnerOrganizerNotFoundException ex = assertThrows(
                MockPartnerOrganizerNotFoundException.class,
                () -> resolver.requireSupportedOrganizer("org3")
        );

        assertEquals("Организатор не найден: org3", ex.getMessage());
    }
}