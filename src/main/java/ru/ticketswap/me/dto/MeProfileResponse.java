package ru.ticketswap.me.dto;

import java.time.Instant;

public record MeProfileResponse(
        Long id,
        String email,
        String login,
        String phoneNumber,
        String role,
        boolean twoFactorEnabled,
        Instant createdAt
) {
}
