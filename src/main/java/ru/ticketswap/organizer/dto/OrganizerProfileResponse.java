package ru.ticketswap.organizer.dto;

import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerVerificationMode;

import java.time.Instant;

public record OrganizerProfileResponse(
        Long id,
        String name,
        String organizerCode,
        String contactEmail,
        OrganizerVerificationMode verificationMode,
        boolean banned,
        String apiKeyLast4,
        Instant apiKeyCreatedAt
) {

    public static OrganizerProfileResponse fromEntity(Organizer organizer) {
        return new OrganizerProfileResponse(
                organizer.getId(),
                organizer.getName(),
                organizer.getOrganizerCode(),
                organizer.getContactEmail(),
                organizer.getVerificationMode(),
                organizer.isBanned(),
                organizer.getApiKeyLast4(),
                organizer.getApiKeyCreatedAt()
        );
    }
}
