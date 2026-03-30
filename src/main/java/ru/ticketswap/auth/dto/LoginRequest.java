package ru.ticketswap.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @JsonAlias({"username", "login", "email", "phone", "phoneNumber"})
        @NotBlank(message = "Identifier is required")
        @Size(max = 255, message = "Identifier must be at most 255 characters")
        String identifier,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password
) {
}
