package ru.ticketswap.mockpartner.data;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class MockPartnerDataProvider {

    private static final Set<String> SUPPORTED_ORGANIZER_CODES = Set.of("org1", "org2");

    public boolean isSupportedOrganizer(String organizerCode) {
        return organizerCode != null && SUPPORTED_ORGANIZER_CODES.contains(organizerCode);
    }

    public List<MockPartnerEventData> getEventsByOrganizerCode(String organizerCode) {
        if (!isSupportedOrganizer(organizerCode)) {
            return List.of();
        }

        return MockPartnerCatalog.events().stream()
                .filter(event -> organizerCode.equals(event.organizerCode()))
                .toList();
    }
}
