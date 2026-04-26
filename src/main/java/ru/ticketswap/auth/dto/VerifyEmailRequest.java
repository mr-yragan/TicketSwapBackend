package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Токен подтверждения обязателен")
        @Size(max = 512, message = "Токен подтверждения слишком длинный")
        String token
) {
}