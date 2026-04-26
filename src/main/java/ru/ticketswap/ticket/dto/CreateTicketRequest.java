package ru.ticketswap.ticket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTicketRequest(
        @NotBlank(message = "UID обязателен")
        @Size(max = 255, message = "UID должен быть не длиннее 255 символов")
        String uid,

        @NotBlank(message = "Название мероприятия обязательно")
        @Size(max = 255, message = "Название мероприятия должно быть не длиннее 255 символов")
        String eventName,

        @NotNull(message = "Дата мероприятия обязательна")
        @Future(message = "Дата мероприятия должна быть в будущем")
        LocalDateTime eventDate,

        @NotBlank(message = "Площадка обязательна")
        @Size(max = 255, message = "Площадка должна быть не длиннее 255 символов")
        String venue,

        @NotNull(message = "Цена обязательна")
        @DecimalMin(value = "0.01", message = "Цена должна быть положительной")
        BigDecimal price,

        @Size(max = 2000, message = "Дополнительная информация должна быть не длиннее 2000 символов")
        String additionalInfo,

        @Size(max = 255, message = "Название организатора должно быть не длиннее 255 символов")
        String organizerName,

        @Size(max = 255, message = "ID мероприятия должен быть не длиннее 255 символов")
        String eventId,

        @Size(max = 2000, message = "Комментарий продавца должен быть не длиннее 2000 символов")
        String sellerComment
) {

    public CreateTicketRequest(
            String uid,
            String eventName,
            LocalDateTime eventDate,
            String venue,
            BigDecimal price,
            String additionalInfo,
            String organizerName,
            String sellerComment
    ) {
        this(uid, eventName, eventDate, venue, price, additionalInfo, organizerName, null, sellerComment);
    }
}