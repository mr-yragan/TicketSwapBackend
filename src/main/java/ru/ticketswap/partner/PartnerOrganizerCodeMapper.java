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
        if (organizerName == null || organizerName.isBlank()) {
            return Optional.empty();
        }
        String normalizedOrganizerName = organizerName.trim();
        return organizerRepository.findByOrganizerCodeIgnoreCase(normalizedOrganizerName)
                .or(() -> organizerRepository.findByNameIgnoreCase(normalizedOrganizerName))
                .filter(organizer -> organizer.isExternalApi() && !organizer.isBanned())
                .map(organizer -> organizer.getOrganizerCode());
    }

    public String normalizeOrganizerName(String organizerName) {
        if (organizerName == null) {
            return null;
        }
        return organizerName.trim().toLowerCase(Locale.ROOT);
    }
}
