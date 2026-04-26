package ru.ticketswap.admin.dto;

import ru.ticketswap.organizer.Organizer;

public record OrganizerResponse(
        Long id,
        String name,
        String apiKey,
        String contactEmail
) {

    public static OrganizerResponse fromEntity(Organizer organizer) {
        return new OrganizerResponse(
                organizer.getId(),
                organizer.getName(),
                organizer.getApiKey(),
                organizer.getContactEmail()
        );
    }
}
