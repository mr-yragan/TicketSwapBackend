package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Verification token is required")
        @Size(max = 512, message = "Verification token is too long")
        String token
) {
}
