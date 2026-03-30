package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TwoFactorVerifyRequest(
        @NotBlank(message = "Challenge ID is required")
        @Size(max = 64, message = "Challenge ID must be at most 64 characters")
        String challengeId,
        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Code must contain exactly 6 digits")
        String code
) {
}
