package ru.ticketswap.me.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorToggleRequest(
        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов")
        String password
) {
}
