package ru.ticketswap.mockpartner.service;

import org.springframework.stereotype.Component;
import ru.ticketswap.mockpartner.data.MockPartnerDataProvider;
import ru.ticketswap.mockpartner.exception.MockPartnerOrganizerNotFoundException;

@Component
public class MockPartnerOrganizerResolver {

    private final MockPartnerDataProvider mockPartnerDataProvider;

    public MockPartnerOrganizerResolver(MockPartnerDataProvider mockPartnerDataProvider) {
        this.mockPartnerDataProvider = mockPartnerDataProvider;
    }

    public String requireSupportedOrganizer(String organizerCode) {
        if (!mockPartnerDataProvider.isSupportedOrganizer(organizerCode)) {
            throw new MockPartnerOrganizerNotFoundException(organizerCode);
        }

        return organizerCode;
    }
}
