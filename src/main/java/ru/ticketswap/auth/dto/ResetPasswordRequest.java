package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Токен обязателен")
        @Size(max = 512, message = "Токен должен быть не длиннее 512 символов")
        String token,
        @NotBlank(message = "Новый пароль обязателен")
        @Size(min = 8, max = 72, message = "Новый пароль должен быть от 8 до 72 символов")
        String newPassword
) {
}