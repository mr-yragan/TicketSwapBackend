package ru.ticketswap.me.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 3, max = 32, message = "login must be between 3 and 32 characters")
        @Pattern(
                regexp = "^(?!.*@)(?![0-9-]{5,32}$)[A-Za-z0-9_.-]+$",
                message = "login may contain only letters, digits, underscore, dot and dash, and must not look like an email or phone number"
        )
        String login,

        @Size(min = 5, max = 32, message = "phoneNumber must be between 5 and 32 characters")
        @Pattern(regexp = "^[+0-9 ()-]+$", message = "phoneNumber has invalid characters")
        String phoneNumber
) {
}
