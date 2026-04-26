package ru.ticketswap.organizer.dto;

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
            String contactEmail
    ) {
    }
}
