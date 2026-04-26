package ru.ticketswap.partner;

import org.springframework.stereotype.Component;
import ru.ticketswap.organizer.OrganizerRepository;

import java.util.Locale;
import java.util.Optional;

@Component
public class PartnerOrganizerCodeMapper {

    private final OrganizerRepository organizerRepository;

    public PartnerOrganizerCodeMapper(OrganizerRepository organizerRepository) {
        this.organizerRepository = organizerRepository;
    }

    public Optional<String> resolveOrganizerCode(String organizerName) {
        String normalizedOrganizerName = normalizeOrganizerName(organizerName);
        if (normalizedOrganizerName == null) {
            return Optional.empty();
        }

        return organizerRepository.findByApiKeyIgnoreCase(normalizedOrganizerName)
                .map(organizer -> organizer.getApiKey());
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
