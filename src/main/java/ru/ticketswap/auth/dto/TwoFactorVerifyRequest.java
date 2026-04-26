package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TwoFactorVerifyRequest(
        @NotBlank(message = "ID проверки обязателен")
        @Size(max = 64, message = "ID проверки должен быть не длиннее 64 символов")
        String challengeId,
        @NotBlank(message = "Код обязателен")
        @Pattern(regexp = "^\\d{6}$", message = "Код должен содержать ровно 6 цифр")
        String code
) {
}