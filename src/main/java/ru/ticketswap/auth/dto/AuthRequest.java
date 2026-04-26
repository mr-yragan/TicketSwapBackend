package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank(message = "Почта обязательна")
        @Email(message = "Почта должна быть корректной")
        @Size(max = 255, message = "Почта должна быть не длиннее 255 символов")
        String email,

        @NotBlank(message = "Логин обязателен")
        @Size(min = 3, max = 32, message = "Логин должен быть от 3 до 32 символов")
        @Pattern(
                regexp = "^(?!.*@)(?![0-9-]{5,32}$)[A-Za-z0-9_.-]+$",
                message = "Логин может содержать только буквы, цифры, нижнее подчёркивание, точку и дефис, а также не должен выглядеть как почта или номер телефона"
        )
        String login,

        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов")
        String password
) {
}