package ru.ticketswap.admin.dto;

import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerVerificationMode;

import java.time.Instant;

public record OrganizerResponse(
        Long id,
        String name,
        String organizerCode,
        String contactEmail,
        OrganizerVerificationMode verificationMode,
        boolean banned,
        String apiKeyLast4,
        Instant apiKeyCreatedAt,
        String generatedIntegrationSecret
) {

    public OrganizerResponse(Long id, String name, String organizerCode, String contactEmail) {
        this(id, name, organizerCode, contactEmail, OrganizerVerificationMode.EXTERNAL_API, false, null, null, null);
    }

    public static OrganizerResponse fromEntity(Organizer organizer) {
        return fromEntity(organizer, null);
    }

    public static OrganizerResponse fromEntity(Organizer organizer, String generatedIntegrationSecret) {
        return new OrganizerResponse(
                organizer.getId(),
                organizer.getName(),
                organizer.getOrganizerCode(),
                organizer.getContactEmail(),
                organizer.getVerificationMode(),
                organizer.isBanned(),
                organizer.getApiKeyLast4(),
                organizer.getApiKeyCreatedAt(),
                generatedIntegrationSecret
        );
    }
}
