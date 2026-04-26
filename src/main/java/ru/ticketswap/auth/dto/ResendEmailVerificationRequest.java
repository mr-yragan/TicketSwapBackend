package ru.ticketswap.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendEmailVerificationRequest(
        @NotBlank(message = "Почта обязательна")
        @Email(message = "Почта должна быть корректной")
        @Size(max = 255, message = "Почта должна быть не длиннее 255 символов")
        String email
) {
}