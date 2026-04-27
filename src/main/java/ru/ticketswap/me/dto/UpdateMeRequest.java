package ru.ticketswap.me.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 3, max = 32, message = "Логин должен быть от 3 до 32 символов")
        @Pattern(
                regexp = "^(?!.*@)[A-Za-z0-9_.-]+$",
                message = "Логин может содержать только буквы, цифры, нижнее подчёркивание, точку и дефис и не должен выглядеть как почта"
        )
        String login
) {
}
