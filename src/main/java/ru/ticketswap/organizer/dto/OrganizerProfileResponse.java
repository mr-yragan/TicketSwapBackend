package ru.ticketswap.organizer.dto;

import ru.ticketswap.organizer.OrganizerVerificationMode;

public record OrganizerProfileResponse(
        UserInfo user,
        OrganizerInfo organizer
) {

    public record UserInfo(
            Long id,
            String email,
            String login,
            String role,
            boolean emailVerified
    ) {
    }

    public record OrganizerInfo(
            Long id,
            String name,
            String apiKey,
            String contactEmail,
            OrganizerVerificationMode verificationMode,
            boolean banned
    ) {
    }
}
