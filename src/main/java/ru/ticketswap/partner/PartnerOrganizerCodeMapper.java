package ru.ticketswap.partner;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class PartnerOrganizerCodeMapper {

    private static final Map<String, String> SUPPORTED_ORGANIZERS = Map.of(
            "org1", "org1",
            "org2", "org2"
    );

    public Optional<String> resolveOrganizerCode(String organizerName) {
        String normalizedOrganizerName = normalizeOrganizerName(organizerName);
        if (normalizedOrganizerName == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(SUPPORTED_ORGANIZERS.get(normalizedOrganizerName));
    }

    public String normalizeOrganizerName(String organizerName) {
        if (organizerName == null) {
            return null;
        }

        String normalized = organizerName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    public boolean isSupportedOrganizer(String organizerName) {
        return resolveOrganizerCode(organizerName).isPresent();
    }
}
