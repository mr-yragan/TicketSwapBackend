package ru.ticketswap.admin.dto;

import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerVerificationMode;

public record OrganizerResponse(
        Long id,
        String name,
        String apiKey,
        String contactEmail,
        OrganizerVerificationMode verificationMode,
        boolean banned
) {

    public OrganizerResponse(Long id, String name, String apiKey, String contactEmail) {
        this(id, name, apiKey, contactEmail, OrganizerVerificationMode.EXTERNAL_API, false);
    }

    public static OrganizerResponse fromEntity(Organizer organizer) {
        return new OrganizerResponse(
                organizer.getId(),
                organizer.getName(),
                organizer.getApiKey(),
                organizer.getContactEmail(),
                organizer.getVerificationMode(),
                organizer.isBanned()
        );
    }
}
