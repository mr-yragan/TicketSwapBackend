package ru.ticketswap.organizer.dto;

import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerVerificationMode;

public record OrganizerCatalogResponse(
        Long id,
        String name,
        OrganizerVerificationMode verificationMode,
        boolean hasExternalApi
) {

    public static OrganizerCatalogResponse fromEntity(Organizer organizer) {
        return new OrganizerCatalogResponse(
                organizer.getId(),
                organizer.getName(),
                organizer.getVerificationMode(),
                organizer.isExternalApi()
        );
    }
}
