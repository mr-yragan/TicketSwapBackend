package ru.ticketswap.organizer.manual.dto;

import jakarta.validation.constraints.Size;

public record ManualReissueRejectRequest(
        @Size(max = 2000, message = "Причина должна быть не длиннее 2000 символов")
        String reason
) {
}
