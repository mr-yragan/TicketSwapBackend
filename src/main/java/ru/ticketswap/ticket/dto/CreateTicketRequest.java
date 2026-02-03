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
        @NotNull(message = "originalPrice is required")
        @DecimalMin(value = "0.01", message = "originalPrice must be positive")
        BigDecimal originalPrice,
        @NotNull(message = "resalePrice is required")
        @DecimalMin(value = "0.01", message = "resalePrice must be positive")
        BigDecimal resalePrice
) {}
