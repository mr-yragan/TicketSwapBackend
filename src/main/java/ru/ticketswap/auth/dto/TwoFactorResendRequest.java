package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorResendRequest(
        @NotBlank(message = "ID проверки обязателен")
        @Size(max = 64, message = "ID проверки должен быть не длиннее 64 символов")
        String challengeId
) {
}