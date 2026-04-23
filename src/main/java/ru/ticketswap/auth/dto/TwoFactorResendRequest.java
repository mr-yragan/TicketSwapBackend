package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorResendRequest(
        @NotBlank(message = "Challenge ID is required")
        @Size(max = 64, message = "Challenge ID must be at most 64 characters")
        String challengeId
) {
}
