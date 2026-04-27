package ru.ticketswap.organizer.manual.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualVerificationDecisionRequest(
        @NotNull(message = "Нужно передать решение по проверке")
        Boolean approved,

        @Size(max = 2000, message = "Комментарий должен быть не длиннее 2000 символов")
        String reason
) {
}
