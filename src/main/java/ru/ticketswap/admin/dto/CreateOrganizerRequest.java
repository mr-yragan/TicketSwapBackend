package ru.ticketswap.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizerRequest(
        @NotBlank(message = "Название обязательно")
        @Size(max = 255, message = "Название должно быть не длиннее 255 символов")
        String name,

        @NotBlank(message = "Контактная почта обязательна")
        @Email(message = "Контактная почта должна быть корректной")
        @Size(max = 255, message = "Контактная почта должна быть не длиннее 255 символов")
        String contactEmail,

        @NotBlank(message = "API-ключ обязателен")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]{2,64}$",
                message = "API-ключ может содержать только буквы, цифры, дефис и нижнее подчёркивание, длина от 2 до 64 символов"
        )
        String apiKey
) {
}