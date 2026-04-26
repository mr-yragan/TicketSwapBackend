package ru.ticketswap.me.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 3, max = 32, message = "Логин должен быть от 3 до 32 символов")
        @Pattern(
                regexp = "^(?!.*@)(?![0-9-]{5,32}$)[A-Za-z0-9_.-]+$",
                message = "Логин может содержать только буквы, цифры, нижнее подчёркивание, точку и дефис, а также не должен выглядеть как почта или номер телефона"
        )
        String login,

        @Size(min = 5, max = 64, message = "Номер телефона должен быть от 5 до 64 символов")
        @Pattern(regexp = "^[+0-9 ()-]+$", message = "Номер телефона содержит недопустимые символы")
        String phoneNumber
) {
}