package ru.ticketswap.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.ticketswap.organizer.OrganizerVerificationMode;

public record CreateOrganizerRequest(
        @Size(max = 255, message = "Название должно быть не длиннее 255 символов")
        String name,

        @Email(message = "Контактная почта должна быть корректной")
        @Size(max = 255, message = "Контактная почта должна быть не длиннее 255 символов")
        String contactEmail,

        @Pattern(
                regexp = "^$|^[A-Za-z0-9_-]{2,64}$",
                message = "Код организатора может содержать только буквы, цифры, дефис и нижнее подчёркивание, длина от 2 до 64 символов"
        )
        String organizerCode,

        @Size(max = 255, message = "Интеграционный секрет должен быть не длиннее 255 символов")
        String integrationSecret,

        /**
         * Deprecated compatibility field. Older scripts sent apiKey as public organizer code.
         */
        @Pattern(
                regexp = "^$|^[A-Za-z0-9_-]{2,64}$",
                message = "API-ключ может содержать только буквы, цифры, дефис и нижнее подчёркивание, длина от 2 до 64 символов"
        )
        String apiKey,

        OrganizerVerificationMode verificationMode
) {

    public CreateOrganizerRequest(String name, String contactEmail, String apiKey) {
        this(name, contactEmail, apiKey, null, apiKey, OrganizerVerificationMode.EXTERNAL_API);
    }
}
