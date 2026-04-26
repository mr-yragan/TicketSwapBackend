package ru.ticketswap.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VenueRequest(
        @NotBlank(message = "Название площадки обязательно")
        @Size(max = 255, message = "Название площадки должно быть не длиннее 255 символов")
        String name,

        @NotBlank(message = "Адрес площадки обязателен")
        @Size(max = 255, message = "Адрес площадки должен быть не длиннее 255 символов")
        String address,

        @NotBlank(message = "Часовой пояс площадки обязателен")
        @Size(max = 64, message = "Часовой пояс площадки должен быть не длиннее 64 символов")
        String timezone
) {
}
