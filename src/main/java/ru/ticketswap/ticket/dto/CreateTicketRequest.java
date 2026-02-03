package ru.ticketswap.ticket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTicketRequest(
        @NotBlank(message = "uid is required")
        @Size(max = 255, message = "uid must be at most 255 characters")
        String uid,

        @NotBlank(message = "eventName is required")
        @Size(max = 255, message = "eventName must be at most 255 characters")
        String eventName,

        @NotNull(message = "eventDate is required")
        @Future(message = "eventDate must be in the future")
        LocalDateTime eventDate,

        @NotBlank(message = "venue is required")
        @Size(max = 255, message = "venue must be at most 255 characters")
        String venue,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be positive")
        BigDecimal price,

        @Size(max = 2000, message = "additionalInfo must be at most 2000 characters")
        String additionalInfo,

        @Size(max = 255, message = "organizerName must be at most 255 characters")
        String organizerName,

        @Size(max = 2000, message = "sellerComment must be at most 2000 characters")
        String sellerComment
) {
}
