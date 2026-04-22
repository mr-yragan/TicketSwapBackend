package ru.ticketswap.organizer.dto;

import java.time.Instant;

public record OrganizerProfileResponse(
        Long id,
        String email,
        String login,
        String role,
        boolean emailVerified,
        Instant createdAt
) {
}
