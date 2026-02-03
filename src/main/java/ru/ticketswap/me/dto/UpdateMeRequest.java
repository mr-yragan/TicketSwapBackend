package ru.ticketswap.me.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMeRequest(
        @Size(min = 3, max = 32, message = "login must be between 3 and 32 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "login may contain only letters, digits, underscore, dot and dash")
        String login,

        @Size(min = 5, max = 32, message = "phoneNumber must be between 5 and 32 characters")
        @Pattern(regexp = "^[+0-9 ()-]+$", message = "phoneNumber has invalid characters")
        String phoneNumber
) {
}
