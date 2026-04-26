package ru.ticketswap.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record OrganizerEventRequest(
        @NotBlank(message = "ID мероприятия обязателен")
        @Size(max = 255, message = "ID мероприятия должен быть не длиннее 255 символов")
        String eventId,

        @NotBlank(message = "Название мероприятия обязательно")
        @Size(max = 255, message = "Название мероприятия должно быть не длиннее 255 символов")
        String name,

        @NotNull(message = "Дата и время начала обязательны")
        @Future(message = "Дата и время начала должны быть в будущем")
        Instant startsAt,

        @Valid
        @NotNull(message = "Площадка обязательна")
        VenueRequest venue
) {
}
