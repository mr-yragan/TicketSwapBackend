package ru.ticketswap.me.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateTwoFactorRequest(
        @NotNull(message = "twoFactorEnabled is required")
        Boolean twoFactorEnabled
) {
}
