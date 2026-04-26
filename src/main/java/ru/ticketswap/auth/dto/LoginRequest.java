package ru.ticketswap.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @JsonAlias({"username", "login", "email", "phone", "phoneNumber"})
        @NotBlank(message = "Идентификатор обязателен")
        @Size(max = 255, message = "Идентификатор должен быть не длиннее 255 символов")
        String identifier,
        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов")
        String password
) {
}